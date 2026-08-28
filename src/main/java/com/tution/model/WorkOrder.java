package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/**
 * A commitment to pay a vendor for a specific piece of work.
 *
 * This is the thing the client asked for by name: one vendor with three orders of
 * Rs. 1,00,000 / Rs. 1,00,000 / Rs. 20,000, and every expense saying which of the
 * three it paid. The order is what was promised; the expenses booked against it
 * are what has actually gone out.
 *
 * TWO DIFFERENT KINDS OF STATE, HELD DIFFERENTLY. {@link #status} is the
 * LIFECYCLE - somebody decides an order is issued, or cancelled - so it is a
 * stored column. Payment progress is arithmetic over the expenses, so it is
 * derived here and never stored; a paid flag would go stale the moment an
 * expense was voided.
 */
public class WorkOrder {

    /** Lifecycle, stored. */
    public static final String DRAFT       = "DRAFT";
    public static final String ISSUED      = "ISSUED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED   = "COMPLETED";
    public static final String CANCELLED   = "CANCELLED";

    /** Payment progress, derived. */
    public static final String UNPAID         = "UNPAID";
    public static final String PARTIALLY_PAID = "PARTIALLY_PAID";
    public static final String FULLY_PAID     = "FULLY_PAID";

    private int        workOrderId;
    private int        vendorId;
    private String     woNo;
    private String     title;
    private String     description;
    private BigDecimal orderValue = Money.ZERO;
    private String     orderDate;
    private String     expectedDate;
    private String     status = DRAFT;
    private String     createdBy;
    private Integer    createdById;
    private Integer    approvedById;
    private String     approvedAt;
    private String     remarks;
    private String     createdAt;

    // Joined / derived - never stored.
    private String     vendorName;
    private BigDecimal paid = Money.ZERO;      // SUM of live expenses on this order
    private int        expenseCount;

    /** What is still owed on this order. Never negative. */
    public BigDecimal getRemaining() {
        BigDecimal left = Money.of(orderValue).subtract(Money.of(paid));
        return left.signum() < 0 ? Money.ZERO : left;
    }

    /** True when the expenses booked exceed the order value. */
    public boolean isOverPaid() {
        return Money.of(orderValue).subtract(Money.of(paid)).signum() < 0;
    }

    /** The single definition of how far along the money is. */
    public String getPaymentStatus() {
        if (Money.of(paid).signum() == 0) return UNPAID;
        if (Money.of(paid).compareTo(Money.of(orderValue)) >= 0) return FULLY_PAID;
        return PARTIALLY_PAID;
    }

    public String getPaymentLabel() {
        switch (getPaymentStatus()) {
            case FULLY_PAID:     return "Fully paid";
            case PARTIALLY_PAID: return "Part paid";
            default:             return "Unpaid";
        }
    }

    public String getStatusLabel() {
        switch (status == null ? "" : status) {
            case DRAFT:       return "Draft";
            case ISSUED:      return "Issued";
            case IN_PROGRESS: return "In progress";
            case COMPLETED:   return "Completed";
            case CANCELLED:   return "Cancelled";
            default:          return status;
        }
    }

    /**
     * Whether an expense may be booked against this order.
     *
     * A DRAFT has not been agreed with anyone yet and a CANCELLED order is one
     * somebody deliberately stopped - paying either would be spending against a
     * commitment that does not exist.
     */
    public boolean canAcceptPayment() {
        return ISSUED.equals(status) || IN_PROGRESS.equals(status) || COMPLETED.equals(status);
    }

    public boolean isApproved() { return approvedById != null || !DRAFT.equals(status); }
    public boolean isDraft()    { return DRAFT.equals(status); }
    public boolean isCancelled(){ return CANCELLED.equals(status); }

    public int getWorkOrderId()               { return workOrderId; }
    public void setWorkOrderId(int v)         { this.workOrderId = v; }

    public int getVendorId()                  { return vendorId; }
    public void setVendorId(int v)            { this.vendorId = v; }

    public String getWoNo()                   { return woNo; }
    public void setWoNo(String v)             { this.woNo = v; }

    public String getTitle()                  { return title; }
    public void setTitle(String v)            { this.title = v; }

    public String getDescription()            { return description; }
    public void setDescription(String v)      { this.description = v; }

    public BigDecimal getOrderValue()         { return orderValue; }
    public void setOrderValue(BigDecimal v)   { this.orderValue = Money.of(v); }

    public String getOrderDate()              { return orderDate; }
    public void setOrderDate(String v)        { this.orderDate = v; }

    public String getExpectedDate()           { return expectedDate; }
    public void setExpectedDate(String v)     { this.expectedDate = v; }

    public String getStatus()                 { return status; }
    public void setStatus(String v)           { this.status = v; }

    public String getCreatedBy()              { return createdBy; }
    public void setCreatedBy(String v)        { this.createdBy = v; }

    public Integer getCreatedById()           { return createdById; }
    public void setCreatedById(Integer v)     { this.createdById = v; }

    public Integer getApprovedById()          { return approvedById; }
    public void setApprovedById(Integer v)    { this.approvedById = v; }

    public String getApprovedAt()             { return approvedAt; }
    public void setApprovedAt(String v)       { this.approvedAt = v; }

    public String getRemarks()                { return remarks; }
    public void setRemarks(String v)          { this.remarks = v; }

    public String getCreatedAt()              { return createdAt; }
    public void setCreatedAt(String v)        { this.createdAt = v; }

    public String getVendorName()             { return vendorName; }
    public void setVendorName(String v)       { this.vendorName = v; }

    public BigDecimal getPaid()               { return paid; }
    public void setPaid(BigDecimal v)         { this.paid = Money.of(v); }

    public int getExpenseCount()              { return expenseCount; }
    public void setExpenseCount(int v)        { this.expenseCount = v; }
}
