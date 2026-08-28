package com.tution.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.tution.model.SubjectScore;

/**
 * Validation for exam setup. Kept out of the servlet and the DAO because these
 * are the rules that decide whether a scanned sheet can be scored at all, and
 * they need to be testable on their own.
 *
 * Two of them are worth stating plainly:
 *
 *  - The subject split must cover every question exactly once. A gap means marks
 *    that belong to nobody; an overlap means marks counted twice. Either way the
 *    subject totals stop adding up to the paper total, and nobody notices until
 *    a parent queries a mark sheet.
 *
 *  - An answer key must be COMPLETE for its booklet. A short key silently scores
 *    the missing questions as unanswered for every candidate who sat that
 *    booklet, which looks like a hard paper rather than a data-entry mistake.
 */
public final class ExamSetupService {

    private ExamSetupService() { }

    /** A validation outcome: the parsed value plus anything wrong with it. */
    public static class Check<T> {
        public T value;
        public final List<String> problems = new ArrayList<>();
        public boolean ok() { return problems.isEmpty(); }
        public String problemText() { return String.join(" ", problems); }
    }

    /* ─── subject split ─── */

    /**
     * Validates the question ranges against the paper length.
     * Names arrive parallel to from/to, as the form submits them.
     */
    public static Check<List<SubjectScore>> subjectMap(String[] names, String[] froms,
                                                       String[] tos, int totalQuestions) {
        Check<List<SubjectScore>> out = new Check<>();
        List<SubjectScore> ranges = new ArrayList<>();
        if (names == null || froms == null || tos == null) {
            out.problems.add("No subject ranges were submitted.");
            out.value = ranges;
            return out;
        }
        int n = Math.min(names.length, Math.min(froms.length, tos.length));
        TreeSet<String> seenNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (int i = 0; i < n; i++) {
            String name = names[i] == null ? "" : names[i].trim();
            String f = froms[i] == null ? "" : froms[i].trim();
            String t = tos[i]   == null ? "" : tos[i].trim();
            if (name.isEmpty() && f.isEmpty() && t.isEmpty()) continue;   // blank spare row

            if (name.isEmpty()) { out.problems.add("A range has no subject name."); continue; }
            if (!seenNames.add(name)) { out.problems.add("Subject '" + name + "' appears twice."); continue; }

            int from = parse(f, -1), to = parse(t, -1);
            if (from < 1 || to < 1) {
                out.problems.add(name + ": from and to must both be question numbers.");
                continue;
            }
            if (to < from) {
                out.problems.add(name + ": last question (" + to + ") is before the first (" + from + ").");
                continue;
            }
            if (from > totalQuestions || to > totalQuestions) {
                out.problems.add(name + ": range " + from + "-" + to
                               + " goes past the paper, which has " + totalQuestions + " questions.");
                continue;
            }
            ranges.add(new SubjectScore(name, from, to));
        }
        out.value = ranges;
        if (!out.problems.isEmpty()) return out;
        if (ranges.isEmpty()) {
            out.problems.add("Add at least one subject range.");
            return out;
        }

        // Every question covered exactly once.
        int[] cover = new int[totalQuestions + 1];
        for (SubjectScore r : ranges) for (int q = r.from; q <= r.to; q++) cover[q]++;

        List<String> gaps = new ArrayList<>(), dupes = new ArrayList<>();
        for (int q = 1; q <= totalQuestions; q++) {
            if (cover[q] == 0) gaps.add(String.valueOf(q));
            else if (cover[q] > 1) dupes.add(String.valueOf(q));
        }
        if (!gaps.isEmpty()) {
            out.problems.add("Question(s) " + condense(gaps) + " are not in any subject.");
        }
        if (!dupes.isEmpty()) {
            out.problems.add("Question(s) " + condense(dupes) + " are in more than one subject.");
        }
        return out;
    }

    /* ─── answer keys ─── */

    /**
     * Parses an answer key and checks it covers the whole paper.
     * Accepts "1:A 2:B ..." or a bare "A,B,C,..." sequence, the two shapes
     * {@code OmrService.parseAnswerKey} already understands.
     */
    public static Check<Map<Integer, String>> answerKey(String raw, int totalQuestions) {
        Check<Map<Integer, String>> out = new Check<>();
        Map<Integer, String> key = new LinkedHashMap<>();
        out.value = key;

        if (raw == null || raw.trim().isEmpty()) return out;    // no key is allowed; a WRONG one is not

        String[] tokens = raw.trim().split("[\\s,;]+");
        int seq = 1;
        for (String tk : tokens) {
            if (tk.isEmpty()) continue;
            String t = tk.trim();
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(\\d+)[:.)\\-]?([A-Da-d])$").matcher(t);
            if (m.matches()) {
                key.put(Integer.parseInt(m.group(1)), m.group(2).toUpperCase());
            } else if (t.length() == 1 && "ABCDabcd".indexOf(t.charAt(0)) >= 0) {
                key.put(seq++, t.toUpperCase());
            } else if (t.length() == 1 && "-_.".indexOf(t.charAt(0)) >= 0) {
                seq++;                                          // deliberately skipped question
            } else {
                out.problems.add("'" + t + "' is not an option - use A, B, C or D.");
                return out;
            }
        }
        List<String> missing = new ArrayList<>(), extra = new ArrayList<>();
        for (int q = 1; q <= totalQuestions; q++) if (!key.containsKey(q)) missing.add(String.valueOf(q));
        for (Integer q : key.keySet()) if (q < 1 || q > totalQuestions) extra.add(String.valueOf(q));

        if (!extra.isEmpty()) {
            out.problems.add("Answer(s) given for question(s) " + condense(extra)
                           + ", but the paper has only " + totalQuestions + ".");
        }
        if (!missing.isEmpty()) {
            out.problems.add("No answer for question(s) " + condense(missing)
                           + ". A partial key scores those as unanswered for everyone who sat this booklet.");
        }
        return out;
    }

    /** The key as a compact "A,B,C,..." line for redisplay in the form. */
    public static String keyToText(Map<Integer, String> key, int totalQuestions) {
        if (key == null || key.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int q = 1; q <= totalQuestions; q++) {
            if (q > 1) sb.append(',');
            String v = key.get(q);
            sb.append(v == null ? "-" : v);
        }
        return sb.toString();
    }

    /* ─── marking scheme ─── */

    /** The paper total. Negative marking does not change the maximum obtainable. */
    public static int maxScore(int totalQuestions, int markCorrect) {
        return Math.max(0, totalQuestions * markCorrect);
    }

    public static Check<int[]> marking(String correct, String wrong, String questions) {
        Check<int[]> out = new Check<>();
        int q = parse(questions, 0), c = parse(correct, Integer.MIN_VALUE), w = parse(wrong, Integer.MIN_VALUE);
        if (q < 1)   out.problems.add("Number of questions must be at least 1.");
        if (q > 400) out.problems.add("Number of questions looks wrong (" + q + ").");
        if (c == Integer.MIN_VALUE || c <= 0) out.problems.add("Marks for a correct answer must be a positive number.");
        if (w == Integer.MIN_VALUE)           out.problems.add("Marks for a wrong answer must be a number (use 0 for no negative marking).");
        else if (w > 0) out.problems.add("Marks for a wrong answer should be zero or negative, not " + w + ".");
        out.value = new int[] { q, c, w };
        return out;
    }

    /* ─── helpers ─── */

    /** "1 2 3 7 8" -> "1-3, 7-8", so a long list of missing questions stays readable. */
    static String condense(List<String> numbers) {
        List<Integer> ns = new ArrayList<>();
        for (String s : numbers) ns.add(Integer.parseInt(s));
        java.util.Collections.sort(ns);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < ns.size()) {
            int start = ns.get(i), end = start;
            while (i + 1 < ns.size() && ns.get(i + 1) == end + 1) { end = ns.get(++i); }
            if (sb.length() > 0) sb.append(", ");
            sb.append(start);
            if (end > start) sb.append('-').append(end);
            i++;
        }
        return sb.toString();
    }

    /** Booklet codes the sheet can express. */
    public static List<String> booklets() {
        return new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
    }

    private static int parse(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }
}
