package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/**
 * One line of the fund ledger: a top-up in, or an expense out.
 *
 * Rows are never edited or deleted. A mistake is corrected by posting a
 * REVERSAL in the opposite direction, so both the original and the correction
 * stay on the statement - "why did the balance change on the 14th?" has to be
 * answerable months later.
 *
 * {@link #getSourceType()} and {@link #getSourceId()} say what put the row here,
 * so a ledger line can always be traced back to the expense or receipt behind
 * it rather than being an unexplained movement.
 */
public class FundTransaction {

    /** Directions. */
    public static final String CREDIT = "CREDIT";
    public static final String DEBIT  = "DEBIT";

    /** Source types, matching the ENUM in the migration. */
    public static final String SRC_TOPUP      = "TOPUP";
    public static final String SRC_EXPENSE    = "EXPENSE";
    public static final String SRC_EXAM_FEE   = "EXAM_FEE";
    public static final String SRC_ADJUSTMENT = "ADJUSTMENT";
    public static final String SRC_REVERSAL   = "REVERSAL";
    /** Payroll. Kept apart from EXPENSE so salary is always identifiable. */
    public static final String SRC_SALARY     = "SALARY";

    private int        txnId;
    private int        fundId;
    private String     fundName;        // joined, for the "all funds" view
    private String     txnDate;
    private String     direction;       // CREDIT / DEBIT
    private BigDecimal amount = Money.ZERO;
    private String     sourceType;
    private Integer    sourceId;
    private String     narration;
    private String     paymentMode;
    private String     txnRef;
    private String     createdBy;
    private Integer    createdById;
    private String     createdAt;

    /**
     * Balance after this row, filled in by the DAO as it walks the statement in
     * order. Not stored - it only means anything relative to the rows before it.
     */
    private BigDecimal runningBalance = Money.ZERO;

    public boolean isCredit() { return CREDIT.equals(direction); }
    public boolean isDebit()  { return DEBIT.equals(direction); }

    /** The amount signed for arithmetic: credits positive, debits negative. */
    public BigDecimal getSignedAmount() {
        return isCredit() ? Money.of(amount) : Money.of(amount).negate();
    }

    /** Human label for the source, used in the statement's Particulars column. */
    public String getSourceLabel() {
        if (sourceType == null) return "";
        switch (sourceType) {
            case SRC_TOPUP:      return "Fund top-up";
            case SRC_EXPENSE:    return "Expense";
            case SRC_EXAM_FEE:   return "Exam fee";
            case SRC_ADJUSTMENT: return "Adjustment";
            case SRC_REVERSAL:   return "Reversal";
            case SRC_SALARY:     return "Salary";
            default:             return sourceType;
        }
    }

    public int getTxnId()                  { return txnId; }
    public void setTxnId(int v)            { this.txnId = v; }

    public int getFundId()                 { return fundId; }
    public void setFundId(int v)           { this.fundId = v; }

    public String getFundName()            { return fundName; }
    public void setFundName(String v)      { this.fundName = v; }

    public String getTxnDate()             { return txnDate; }
    public void setTxnDate(String v)       { this.txnDate = v; }

    public String getDirection()           { return direction; }
    public void setDirection(String v)     { this.direction = v; }

    public BigDecimal getAmount()          { return amount; }
    public void setAmount(BigDecimal v)    { this.amount = Money.of(v); }

    public String getSourceType()          { return sourceType; }
    public void setSourceType(String v)    { this.sourceType = v; }

    public Integer getSourceId()           { return sourceId; }
    public void setSourceId(Integer v)     { this.sourceId = v; }

    public String getNarration()           { return narration; }
    public void setNarration(String v)     { this.narration = v; }

    public String getPaymentMode()         { return paymentMode; }
    public void setPaymentMode(String v)   { this.paymentMode = v; }

    public String getTxnRef()              { return txnRef; }
    public void setTxnRef(String v)        { this.txnRef = v; }

    public String getCreatedBy()           { return createdBy; }
    public void setCreatedBy(String v)     { this.createdBy = v; }

    public Integer getCreatedById()        { return createdById; }
    public void setCreatedById(Integer v)  { this.createdById = v; }

    public String getCreatedAt()           { return createdAt; }
    public void setCreatedAt(String v)     { this.createdAt = v; }

    public BigDecimal getRunningBalance()  { return runningBalance; }
    public void setRunningBalance(BigDecimal v) { this.runningBalance = Money.of(v); }
}
