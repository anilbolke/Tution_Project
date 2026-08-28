package com.tution.service;

import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tution.dao.CandidateDAO;
import com.tution.dao.ExamSetupDAO;
import com.tution.model.Exam;
import com.tution.model.ExamCandidate;
import com.tution.model.OmrResult;
import com.tution.model.OmrTemplate;
import com.tution.model.SubjectScore;
import com.tution.util.RollNumber;

/**
 * Turns one scanned answer sheet into a scored result, or into a review item.
 *
 * The guiding rule of this class is that it NEVER guesses. Every way of failing
 * to identify a sheet has its own outcome and lands in the review queue with the
 * image attached, because the alternative — a plausible-looking best guess —
 * files one child's marks against another child's name, and on an exam that
 * decides fee concessions nobody would find out.
 */
public class ExamScanService {

    /** Why a sheet could not be scored, or MATCHED when it could. */
    public enum Match {
        MATCHED,
        NO_ROLL,        // roll bubbles blank, ambiguous, or fewer than 6 digits read
        BAD_CHECKSUM,   // 6 digits read but the check digit disagrees
        NOT_IN_EXAM,    // valid roll, but nobody in this exam holds it
        DUPLICATE,      // this candidate already has a result
        NO_BOOKLET      // booklet bubble blank or ambiguous - no key can be chosen
    }

    /** Everything read off one sheet, plus the verdict. */
    public static class Sheet {
        public String fileName;
        public String rollRead = "";
        public String bookletRead;
        public String boardRead;
        public String examNameRead;
        public Match  match;
        public boolean lowRegistration;
        public ExamCandidate candidate;

        public OmrResult omr;
        public int attempted, correct, wrong, blank, rawScore, maxScore;
        public double percentage, scholarshipPct;
        public Integer awardedRuleId;
        public String awardLabel;
        public final List<SubjectScore> subjects = new ArrayList<>();
        public String note;

        public boolean scored() { return match == Match.MATCHED; }
        public String studentName() { return candidate == null ? "" : candidate.getFullName(); }
    }

    private final CandidateDAO candidateDAO = new CandidateDAO();
    private final ExamSetupDAO setupDAO = new ExamSetupDAO();
    private final ScholarshipService scholarship = new ScholarshipService();

    /** Everything about an exam that scanning needs, loaded once per batch. */
    public static class Context {
        public Exam exam;
        public OmrTemplate template;
        public List<SubjectScore> subjectMap;
        public Map<String, Map<Integer, String>> keys = new LinkedHashMap<>();
        public OmrService omr;
    }

    public Context load(Exam exam, OmrTemplate template) throws SQLException {
        Context c = new Context();
        c.exam = exam;
        c.template = template;
        c.subjectMap = setupDAO.subjectMap(exam.getExamId());
        for (String b : ExamSetupService.booklets()) {
            Map<Integer, String> k = setupDAO.answerKey(exam.getExamId(), b);
            if (!k.isEmpty()) c.keys.put(b, k);
        }
        c.omr = new OmrService(template);
        return c;
    }

    /**
     * Reads and, where possible, scores one sheet. Nothing is written here —
     * the caller persists, so a batch can be shown before it is committed.
     */
    public Sheet process(Context ctx, BufferedImage img, String fileName,
                         java.util.Set<String> rollsSeenThisBatch) throws SQLException {
        Sheet s = new Sheet();
        s.fileName = fileName;

        s.omr = ctx.omr.read(img);
        s.lowRegistration = ctx.omr.isLowRegistration();
        s.rollRead      = ctx.omr.readRoll(img);
        s.bookletRead   = ctx.omr.readRegion(img, "booklet");
        s.boardRead     = ctx.omr.readRegion(img, "board");
        s.examNameRead  = ctx.omr.readRegion(img, "exam_name");

        // 1. is there a roll number at all?
        if (s.rollRead == null || s.rollRead.length() != RollNumber.DIGITS) {
            s.match = Match.NO_ROLL;
            s.note = s.rollRead == null || s.rollRead.isEmpty()
                   ? "No roll number could be read."
                   : "Only " + s.rollRead.length() + " of " + RollNumber.DIGITS
                     + " roll digits were readable (" + s.rollRead + ").";
            return s;
        }

        // 2. does it survive the check digit? A single misread bubble would
        //    otherwise turn one valid roll straight into another valid roll.
        ExamCandidate c = candidateDAO.findByRoll(ctx.exam.getExamId(), s.rollRead);
        if (c == null && !RollNumber.isValid(s.rollRead)) {
            s.match = Match.BAD_CHECKSUM;
            s.note = "Roll " + s.rollRead + " fails its check digit, so at least one bubble was misread.";
            return s;
        }
        // 3. valid-looking, but is it in THIS exam?
        if (c == null) {
            s.match = Match.NOT_IN_EXAM;
            s.note = "Roll " + s.rollRead + " is not registered for this exam.";
            return s;
        }
        s.candidate = c;

        // 4. have we already scored this candidate?
        if (rollsSeenThisBatch != null && !rollsSeenThisBatch.add(s.rollRead)) {
            s.match = Match.DUPLICATE;
            s.note = "Roll " + s.rollRead + " (" + c.getFullName() + ") appears twice in this batch.";
            return s;
        }

        // 5. which paper did they sit? Never defaulted - the wrong key would
        //    score a correct paper as mostly wrong.
        String booklet = s.bookletRead;
        if (booklet == null || !ctx.keys.containsKey(booklet)) {
            s.match = Match.NO_BOOKLET;
            s.note = booklet == null
                   ? "The booklet code bubble is blank or ambiguous, so no answer key can be chosen."
                   : "Booklet " + booklet + " has no answer key set up for this exam.";
            return s;
        }

        Map<Integer, String> key = ctx.keys.get(booklet);
        score(ctx, s, key);
        s.match = Match.MATCHED;
        return s;
    }

    /** Applies the key, the subject split, the percentage and the scholarship. */
    private void score(Context ctx, Sheet s, Map<Integer, String> key) throws SQLException {
        Exam e = ctx.exam;
        ctx.omr.score(s.omr, key, e.getMarkCorrect(), e.getMarkWrong());
        ctx.omr.subjectBreakdown(s.omr, key, e.getMarkCorrect(), e.getMarkWrong(), ctx.subjectMap);

        int attempted = 0;
        for (String v : s.omr.getAnswers().values()) if (v != null) attempted++;

        s.attempted = attempted;
        s.correct   = s.omr.getCorrect();
        s.wrong     = s.omr.getWrong();
        s.blank     = e.getTotalQuestions() - attempted;
        s.rawScore  = s.omr.getScore();
        s.maxScore  = e.getMaxScore();
        s.percentage = ScholarshipService.percentage(s.rawScore, s.maxScore);
        s.subjects.addAll(s.omr.getSubjects());

        ScholarshipService.Outcome out = scholarship.evaluate(
                e.getExamType(), s.percentage, s.candidate.getInquiryId());
        s.scholarshipPct = out.pct();
        s.awardedRuleId  = out.winningRuleId();
        s.awardLabel     = out.winner == null ? "" : out.winner.label();
        if (out.applicable.size() > 1) {
            s.note = "Highest of " + out.applicable.size() + " applicable scholarship routes.";
        }
    }
}
