package com.tution.model;

import java.math.BigDecimal;

/**
 * One entry in a fund's lifecycle history: created, closed, reopened, deleted.
 *
 * The name and balance are a SNAPSHOT rather than a live lookup. A DELETE row
 * describes a fund that no longer exists, so there is nothing left to look up -
 * these columns are the only surviving record of what was removed.
 */
public class FundAudit {

    public static final String CREATE = "CREATE";
    public static final String RENAME = "RENAME";
    public static final String CLOSE  = "CLOSE";
    public static final String REOPEN = "REOPEN";
    public static final String DELETE = "DELETE";

    private int        auditId;
    private int        fundId;
    private String     action;
    private String     fundName;
    /** RENAME only: what the fund was called before. Null for every other action. */
    private String     oldName;
    private BigDecimal balance;
    private int        txnCount;
    private String     reason;
    private String     actedBy;
    private Integer    actedById;
    private String     actedAt;

    /** Wording for the screen — "Deleted", not "DELETE". */
    public String getActionLabel() {
        if (action == null) return "";
        switch (action) {
            case CREATE: return "Created";
            case RENAME: return "Renamed";
            case CLOSE:  return "Closed";
            case REOPEN: return "Reopened";
            case DELETE: return "Deleted";
            default:     return action;
        }
    }

    public boolean isDelete() { return DELETE.equals(action); }
    public boolean isRename() { return RENAME.equals(action); }

    public int     getAuditId()            { return auditId; }
    public void    setAuditId(int v)       { this.auditId = v; }

    public int     getFundId()             { return fundId; }
    public void    setFundId(int v)        { this.fundId = v; }

    public String  getAction()             { return action; }
    public void    setAction(String v)     { this.action = v; }

    public String  getFundName()           { return fundName; }
    public void    setFundName(String v)   { this.fundName = v; }

    public String  getOldName()            { return oldName; }
    public void    setOldName(String v)    { this.oldName = v; }

    public BigDecimal getBalance()         { return balance; }
    public void    setBalance(BigDecimal v){ this.balance = v; }

    public int     getTxnCount()           { return txnCount; }
    public void    setTxnCount(int v)      { this.txnCount = v; }

    public String  getReason()             { return reason; }
    public void    setReason(String v)     { this.reason = v; }

    public String  getActedBy()            { return actedBy; }
    public void    setActedBy(String v)    { this.actedBy = v; }

    public Integer getActedById()          { return actedById; }
    public void    setActedById(Integer v) { this.actedById = v; }

    public String  getActedAt()            { return actedAt; }
    public void    setActedAt(String v)    { this.actedAt = v; }
}
