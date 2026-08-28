package com.tution.model;

/** One MCQ on an online exam's paper. */
public class OnlineExamQuestion {

    private int    questionId;
    private int    onlineExamId;
    private String subject;
    private String chapter;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;    // A / B / C / D
    private String difficulty = "MEDIUM";
    private int    marks = 1;
    private int    sortOrder;

    /** The option text for a given letter (A-D), or null for anything else. */
    public String optionFor(String letter) {
        if (letter == null) return null;
        switch (letter.toUpperCase()) {
            case "A": return optionA;
            case "B": return optionB;
            case "C": return optionC;
            case "D": return optionD;
            default:  return null;
        }
    }

    public int    getQuestionId()             { return questionId; }
    public void   setQuestionId(int v)        { this.questionId = v; }

    public int    getOnlineExamId()           { return onlineExamId; }
    public void   setOnlineExamId(int v)      { this.onlineExamId = v; }

    public String getSubject()                { return subject; }
    public void   setSubject(String v)        { this.subject = v; }

    public String getChapter()                { return chapter; }
    public void   setChapter(String v)        { this.chapter = v; }

    public String getQuestionText()           { return questionText; }
    public void   setQuestionText(String v)   { this.questionText = v; }

    public String getOptionA()                { return optionA; }
    public void   setOptionA(String v)        { this.optionA = v; }

    public String getOptionB()                { return optionB; }
    public void   setOptionB(String v)        { this.optionB = v; }

    public String getOptionC()                { return optionC; }
    public void   setOptionC(String v)        { this.optionC = v; }

    public String getOptionD()                { return optionD; }
    public void   setOptionD(String v)        { this.optionD = v; }

    public String getCorrectAnswer()          { return correctAnswer; }
    public void   setCorrectAnswer(String v)  { this.correctAnswer = v; }

    public String getDifficulty()             { return difficulty; }
    public void   setDifficulty(String v)     { this.difficulty = v; }

    public int    getMarks()                  { return marks; }
    public void   setMarks(int v)             { this.marks = v; }

    public int    getSortOrder()              { return sortOrder; }
    public void   setSortOrder(int v)         { this.sortOrder = v; }
}
