package com.tution.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed result of one OMR answer-sheet scan. */
public class OmrResult {

    private int page;
    private int startQ;
    private int endQ;
    private final Map<Integer, String> answers     = new LinkedHashMap<>(); // qno -> A/B/C/D or null
    private final Map<Integer, String> confidence  = new LinkedHashMap<>(); // qno -> high/medium/low
    private final List<Integer> blanks    = new ArrayList<>();
    private final List<Integer> ambiguous = new ArrayList<>();
    private String rawJson;

    // scoring (set only when an answer key was provided)
    private boolean scored;
    private int correct;
    private int wrong;
    private int score;
    private final Map<Integer, String> correctKey = new LinkedHashMap<>();   // qno -> correct option
    private final List<SubjectScore> subjects = new ArrayList<>();           // per-subject breakdown

    public List<SubjectScore> getSubjects() { return subjects; }

    public int attempted() {
        int n = 0;
        for (String a : answers.values()) if (a != null) n++;
        return n;
    }
    public int blankCount() { return answers.size() - attempted(); }
    public int total()      { return answers.size(); }

    public int    getPage()                 { return page; }
    public void   setPage(int v)            { this.page = v; }
    public int    getStartQ()               { return startQ; }
    public void   setStartQ(int v)          { this.startQ = v; }
    public int    getEndQ()                 { return endQ; }
    public void   setEndQ(int v)            { this.endQ = v; }

    public Map<Integer, String> getAnswers()    { return answers; }
    public Map<Integer, String> getConfidence() { return confidence; }
    public List<Integer> getBlanks()            { return blanks; }
    public List<Integer> getAmbiguous()         { return ambiguous; }

    public String getRawJson()              { return rawJson; }
    public void   setRawJson(String v)      { this.rawJson = v; }

    public boolean isScored()               { return scored; }
    public void    setScored(boolean v)     { this.scored = v; }
    public int     getCorrect()             { return correct; }
    public void    setCorrect(int v)        { this.correct = v; }
    public int     getWrong()               { return wrong; }
    public void    setWrong(int v)          { this.wrong = v; }
    public int     getScore()               { return score; }
    public void    setScore(int v)          { this.score = v; }
    public Map<Integer, String> getCorrectKey() { return correctKey; }
}
