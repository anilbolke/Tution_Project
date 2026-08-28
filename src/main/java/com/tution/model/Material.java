package com.tution.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A learning resource: a study-material PDF or an e-content YouTube video. */
public class Material {

    private int    materialId;
    private String title;
    private String className;
    private String subject;
    private String type;        // PDF | VIDEO
    private String filePath;
    private String youtubeUrl;
    private String uploadedBy;
    private String createdAt;

    private static final Pattern YT = Pattern.compile(
        "(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|embed/|shorts/|v/))([A-Za-z0-9_-]{11})");

    /** Extracts the 11-char YouTube id, or "" if not parseable. */
    public String youtubeId() {
        if (youtubeUrl == null) return "";
        Matcher m = YT.matcher(youtubeUrl);
        return m.find() ? m.group(1) : "";
    }

    public boolean isVideo() { return "VIDEO".equals(type); }

    public int    getMaterialId()          { return materialId; }
    public void   setMaterialId(int v)     { this.materialId = v; }

    public String getTitle()               { return title; }
    public void   setTitle(String v)       { this.title = v; }

    public String getClassName()           { return className; }
    public void   setClassName(String v)   { this.className = v; }

    public String getSubject()             { return subject; }
    public void   setSubject(String v)     { this.subject = v; }

    public String getType()                { return type; }
    public void   setType(String v)        { this.type = v; }

    public String getFilePath()            { return filePath; }
    public void   setFilePath(String v)    { this.filePath = v; }

    public String getYoutubeUrl()          { return youtubeUrl; }
    public void   setYoutubeUrl(String v)  { this.youtubeUrl = v; }

    public String getUploadedBy()          { return uploadedBy; }
    public void   setUploadedBy(String v)  { this.uploadedBy = v; }

    public String getCreatedAt()           { return createdAt; }
    public void   setCreatedAt(String v)   { this.createdAt = v; }
}
