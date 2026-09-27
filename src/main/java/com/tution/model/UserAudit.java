package com.tution.model;

/**
 * One change to a login account.
 *
 * The username and name are a snapshot: this row has to still say who was
 * affected even after that person is renamed, so it does not look them up.
 */
public class UserAudit {

    public static final String CREATE   = "CREATE";
    public static final String UPDATE   = "UPDATE";
    public static final String ROLE     = "ROLE";
    public static final String PASSWORD = "PASSWORD";
    public static final String ENABLE   = "ENABLE";
    public static final String DISABLE  = "DISABLE";
    public static final String MANAGER  = "MANAGER";   // reports_to changed

    private int     auditId;
    private int     targetUserId;
    private String  targetUsername;
    private String  targetName;
    private String  action;
    private String  detail;
    private String  actedBy;
    private Integer actedById;
    private String  actedAt;

    public String getActionLabel() {
        if (action == null) return "";
        switch (action) {
            case CREATE:   return "Created";
            case UPDATE:   return "Details changed";
            case ROLE:     return "Role changed";
            case PASSWORD: return "Password reset";
            case ENABLE:   return "Switched on";
            case DISABLE:  return "Switched off";
            case MANAGER:  return "Reports to changed";
            default:       return action;
        }
    }

    /** True for the changes that alter what somebody can reach. */
    public boolean isPrivilegeChange() {
        return ROLE.equals(action) || PASSWORD.equals(action)
            || ENABLE.equals(action) || CREATE.equals(action);
    }

    public int     getAuditId()                 { return auditId; }
    public void    setAuditId(int v)            { this.auditId = v; }
    public int     getTargetUserId()            { return targetUserId; }
    public void    setTargetUserId(int v)       { this.targetUserId = v; }
    public String  getTargetUsername()          { return targetUsername; }
    public void    setTargetUsername(String v)  { this.targetUsername = v; }
    public String  getTargetName()              { return targetName; }
    public void    setTargetName(String v)      { this.targetName = v; }
    public String  getAction()                  { return action; }
    public void    setAction(String v)          { this.action = v; }
    public String  getDetail()                  { return detail; }
    public void    setDetail(String v)          { this.detail = v; }
    public String  getActedBy()                 { return actedBy; }
    public void    setActedBy(String v)         { this.actedBy = v; }
    public Integer getActedById()               { return actedById; }
    public void    setActedById(Integer v)      { this.actedById = v; }
    public String  getActedAt()                 { return actedAt; }
    public void    setActedAt(String v)         { this.actedAt = v; }
}
