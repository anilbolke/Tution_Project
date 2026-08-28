package com.tution.model;

/** Per-subject breakdown for one answer sheet (e.g. Physics Q1-45). */
public class SubjectScore {
    public String name;
    public int from;
    public int to;
    public int questions;   // to - from + 1
    public int attempted;
    public int correct;
    public int wrong;
    public int score;
    public boolean scored;  // true if an answer key covered this range

    public SubjectScore() { }
    public SubjectScore(String name, int from, int to) {
        this.name = name; this.from = from; this.to = to; this.questions = Math.max(0, to - from + 1);
    }
}
