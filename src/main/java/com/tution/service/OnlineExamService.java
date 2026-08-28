package com.tution.service;

import java.util.ArrayList;
import java.util.List;

import com.tution.model.OnlineExamQuestion;

/**
 * Parses and validates the pipe-separated bulk question format for online
 * exams. Kept out of the servlet and the DAO, same reasoning as
 * {@link ExamSetupService}: this is the rule that decides whether a pasted
 * question bank is actually usable, and it needs to be testable on its own.
 *
 * All-or-nothing: if any line is bad, nothing is inserted. A partial paste
 * (half the questions in, half silently dropped) is worse than making the
 * teacher fix the sheet and paste again.
 */
public final class OnlineExamService {

    private OnlineExamService() { }

    private static final int FIELD_COUNT = 11;
    private static final java.util.Set<String> DIFFICULTIES =
            new java.util.HashSet<>(java.util.Arrays.asList("EASY", "MEDIUM", "HARD"));

    /** A validation outcome: the parsed rows plus anything wrong with the paste. */
    public static class Check {
        public List<OnlineExamQuestion> value = new ArrayList<>();
        public final List<String> problems = new ArrayList<>();
        public boolean ok() { return problems.isEmpty(); }
        public String problemText() { return String.join(" | ", problems); }
    }

    /**
     * Parses every non-blank line as
     * {@code CLASS|SUBJECT|CHAPTER|QUESTION_TEXT|OPTION_A|OPTION_B|OPTION_C|OPTION_D|CORRECT_ANSWER|DIFFICULTY|MARKS}.
     *
     * @param raw           the pasted text, one question per line
     * @param expectedClass the exam's own class — every line's CLASS must match it,
     *                      so a sheet meant for a different class is caught, not silently mixed in
     */
    public static Check parseQuestions(String raw, String expectedClass) {
        Check out = new Check();
        if (raw == null || raw.trim().isEmpty()) {
            out.problems.add("Paste at least one question.");
            return out;
        }

        String[] lines = raw.split("\\r?\\n");
        int lineNo = 0;
        int order = 0;
        for (String line : lines) {
            lineNo++;
            String l = line.trim();
            if (l.isEmpty()) continue;

            String[] f = l.split("\\|", -1);
            if (f.length != FIELD_COUNT) {
                out.problems.add("Line " + lineNo + ": expected " + FIELD_COUNT
                        + " fields separated by '|', found " + f.length + ".");
                continue;
            }
            for (int i = 0; i < f.length; i++) f[i] = f[i].trim();

            String cls        = f[0];
            String subject     = f[1];
            String chapter     = f[2];
            String questionText = f[3];
            String optA = f[4], optB = f[5], optC = f[6], optD = f[7];
            String correct     = f[8].toUpperCase();
            String difficulty  = f[9].isEmpty() ? "MEDIUM" : f[9].toUpperCase();
            String marksRaw    = f[10];

            List<String> lineProblems = new ArrayList<>();
            if (expectedClass != null && !expectedClass.isEmpty()
                    && !expectedClass.equalsIgnoreCase(cls)) {
                lineProblems.add("class '" + cls + "' does not match this exam's class '" + expectedClass + "'");
            }
            if (questionText.isEmpty()) lineProblems.add("question text is blank");
            if (optA.isEmpty() || optB.isEmpty() || optC.isEmpty() || optD.isEmpty()) {
                lineProblems.add("one or more options are blank");
            }
            if (!"A".equals(correct) && !"B".equals(correct) && !"C".equals(correct) && !"D".equals(correct)) {
                lineProblems.add("correct answer must be A, B, C or D (got '" + f[8] + "')");
            }
            if (!DIFFICULTIES.contains(difficulty)) {
                lineProblems.add("difficulty must be EASY, MEDIUM or HARD (got '" + f[9] + "')");
            }
            int marks = parseInt(marksRaw, -1);
            if (marks < 1) {
                lineProblems.add("marks must be a whole number of 1 or more (got '" + marksRaw + "')");
            }

            if (!lineProblems.isEmpty()) {
                out.problems.add("Line " + lineNo + ": " + String.join(", ", lineProblems) + ".");
                continue;
            }

            OnlineExamQuestion q = new OnlineExamQuestion();
            q.setSubject(subject);
            q.setChapter(chapter);
            q.setQuestionText(questionText);
            q.setOptionA(optA);
            q.setOptionB(optB);
            q.setOptionC(optC);
            q.setOptionD(optD);
            q.setCorrectAnswer(correct);
            q.setDifficulty(difficulty);
            q.setMarks(marks);
            q.setSortOrder(order++);
            out.value.add(q);
        }

        if (out.problems.isEmpty() && out.value.isEmpty()) {
            out.problems.add("Paste at least one question.");
        }
        return out;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
