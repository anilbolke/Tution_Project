package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/**
 * One payment out of a fund: the voucher.
 *
 * This is where the client's three requirements meet. It debits the fund, it can
 * name a vendor, and it can name WHICH of that vendor's work orders it settles -
 * so an expense against a vendor with three orders of Rs. 1,00,000 / Rs. 1,00,000
 * / Rs. 20,000 is never ambiguous about which one was paid.
 *
 * Both links are optional and mean different things when absent:
 *   vendor_id null     - a general expense, nobody in particular was paid
 *   work_order_id null - a vendor payment outside any work order
 * What is NOT allowed is a work order belonging to a different vendor than the
 * one named; that rule lives in ExpenseService because the database cannot say it.
 */
public class Expense {

    public static final String ACTIVE = "ACTIVE";
    public static final String VOID   = "VOID";

    private int        expenseId;
    private int        fundId;
    private String     voucherNo;         // EXP-2627-0142
    private String     expenseDate;
    private Integer    categoryId;
    private Integer    vendorId;
    private Integer    workOrderId;
    private BigDecimal amount    = Money.ZERO;
    private BigDecimal taxAmount = Money.ZERO;
    private String     paymentMode = "Cash";
    private String     txnRef;
    private String     invoiceNo;
    private String     billPath;
    private String     description;
    private String     status = ACTIVE;
    private Integer    voidedById;
    private String     voidedAt;
    private String     voidReason;
    private String     createdBy;
    private Integer    createdById;
    private String     createdAt;

    // Joined for display - never stored.
    private String     fundName;
    private String     categoryName;
    private String     vendorName;
    private String     woNo;
    private String     woTitle;

    public boolean isVoid() { return VOID.equals(status); }

    /**
     * The tax already inside {@link #amount}, not on top of it.
     *
     * `amount` is the money that left the fund, full stop. Treating tax as an
     * addition would make the fund debit disagree with the voucher and let a work
     * order look over-paid on a payment that was within its value.
     */
    public BigDecimal getNetOfTax() {
        BigDecimal net = Money.of(amount).subtract(Money.of(taxAmount));
        return net.signum() < 0 ? Money.ZERO : net;
    }

    public boolean hasVendor()    { return vendorId    != null && vendorId    > 0; }
    public boolean hasWorkOrder() { return workOrderId != null && workOrderId > 0; }

    public int getExpenseId()                 { return expenseId; }
    public void setExpenseId(int v)           { this.expenseId = v; }

    public int getFundId()                    { return fundId; }
    public void setFundId(int v)              { this.fundId = v; }

    public String getVoucherNo()              { return voucherNo; }
    public void setVoucherNo(String v)        { this.voucherNo = v; }

    public String getExpenseDate()            { return expenseDate; }
    public void setExpenseDate(String v)      { this.expenseDate = v; }

    public Integer getCategoryId()            { return categoryId; }
    public void setCategoryId(Integer v)      { this.categoryId = v; }

    public Integer getVendorId()              { return vendorId; }
    public void setVendorId(Integer v)        { this.vendorId = v; }

    public Integer getWorkOrderId()           { return workOrderId; }
    public void setWorkOrderId(Integer v)     { this.workOrderId = v; }

    public BigDecimal getAmount()             { return amount; }
    public void setAmount(BigDecimal v)       { this.amount = Money.of(v); }

    public BigDecimal getTaxAmount()          { return taxAmount; }
    public void setTaxAmount(BigDecimal v)    { this.taxAmount = Money.of(v); }

    public String getPaymentMode()            { return paymentMode; }
    public void setPaymentMode(String v)      { this.paymentMode = v; }

    public String getTxnRef()                 { return txnRef; }
    public void setTxnRef(String v)           { this.txnRef = v; }

    public String getInvoiceNo()              { return invoiceNo; }
    public void setInvoiceNo(String v)        { this.invoiceNo = v; }

    public String getBillPath()               { return billPath; }
    public void setBillPath(String v)         { this.billPath = v; }

    public String getDescription()            { return description; }
    public void setDescription(String v)      { this.description = v; }

    public String getStatus()                 { return status; }
    public void setStatus(String v)           { this.status = v; }

    public Integer getVoidedById()            { return voidedById; }
    public void setVoidedById(Integer v)      { this.voidedById = v; }

    public String getVoidedAt()               { return voidedAt; }
    public void setVoidedAt(String v)         { this.voidedAt = v; }

    public String getVoidReason()             { return voidReason; }
    public void setVoidReason(String v)       { this.voidReason = v; }

    public String getCreatedBy()              { return createdBy; }
    public void setCreatedBy(String v)        { this.createdBy = v; }

    public Integer getCreatedById()           { return createdById; }
    public void setCreatedById(Integer v)     { this.createdById = v; }

    public String getCreatedAt()              { return createdAt; }
    public void setCreatedAt(String v)        { this.createdAt = v; }

    public String getFundName()               { return fundName; }
    public void setFundName(String v)         { this.fundName = v; }

    public String getCategoryName()           { return categoryName; }
    public void setCategoryName(String v)     { this.categoryName = v; }

    public String getVendorName()             { return vendorName; }
    public void setVendorName(String v)       { this.vendorName = v; }

    public String getWoNo()                   { return woNo; }
    public void setWoNo(String v)             { this.woNo = v; }

    public String getWoTitle()                { return woTitle; }
    public void setWoTitle(String v)          { this.woTitle = v; }
}
