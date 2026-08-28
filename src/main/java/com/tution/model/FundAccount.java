package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/**
 * A pot of money the institute spends from - "Main Office Fund", a branch petty
 * cash float, and so on. Management credits it; every expense debits it.
 *
 * THE BALANCE IS NOT STORED. There is no balance column on fund_accounts and
 * there is deliberately no setter that invents one: {@link #getBalance()} is
 * computed from the opening balance plus the credits and debits the DAO reads
 * out of fund_transactions.
 *
 * A stored balance drifts the first time two people post at once, or an expense
 * is edited, or a transaction rolls back after the column was already written -
 * and once it has drifted there is no way to know what the right number was.
 */
public class FundAccount {

    private int        fundId;
    private String     name;
    private String     description;
    private BigDecimal openingBalance = Money.ZERO;
    private String     openingDate;
    private boolean    active = true;
    private String     createdBy;
    private String     createdAt;

    /* ── derived from fund_transactions; filled in by FundDAO ── */
    private BigDecimal credits = Money.ZERO;   // sum of CREDIT rows
    private BigDecimal debits  = Money.ZERO;   // sum of DEBIT rows
    private int        txnCount;

    /** opening + credits - debits. The only place a balance is ever produced. */
    public BigDecimal getBalance() {
        return Money.of(openingBalance).add(Money.of(credits)).subtract(Money.of(debits));
    }

    /** True when more has been spent than the fund ever held. */
    public boolean isOverdrawn() {
        return getBalance().signum() < 0;
    }

    public int getFundId()                 { return fundId; }
    public void setFundId(int v)           { this.fundId = v; }

    public String getName()                { return name; }
    public void setName(String v)          { this.name = v; }

    public String getDescription()         { return description; }
    public void setDescription(String v)   { this.description = v; }

    public BigDecimal getOpeningBalance()  { return openingBalance; }
    public void setOpeningBalance(BigDecimal v) { this.openingBalance = Money.of(v); }

    public String getOpeningDate()         { return openingDate; }
    public void setOpeningDate(String v)   { this.openingDate = v; }

    public boolean isActive()              { return active; }
    public void setActive(boolean v)       { this.active = v; }

    public String getCreatedBy()           { return createdBy; }
    public void setCreatedBy(String v)     { this.createdBy = v; }

    public String getCreatedAt()           { return createdAt; }
    public void setCreatedAt(String v)     { this.createdAt = v; }

    public BigDecimal getCredits()         { return credits; }
    public void setCredits(BigDecimal v)   { this.credits = Money.of(v); }

    public BigDecimal getDebits()          { return debits; }
    public void setDebits(BigDecimal v)    { this.debits = Money.of(v); }

    public int getTxnCount()               { return txnCount; }
    public void setTxnCount(int v)         { this.txnCount = v; }
}
