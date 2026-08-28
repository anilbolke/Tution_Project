package com.tution.model;

import java.math.BigDecimal;

import com.tution.util.Money;

/**
 * Somebody the tuition pays: the printer, the landlord, the housekeeping agency.
 *
 * The money figures on here are DERIVED - ordered is the sum of the vendor's live
 * work orders, paid is the sum of the expenses booked against them. Storing a
 * running balance per vendor would drift the first time an expense was voided,
 * and a vendor balance that disagrees with the expense register is worse than no
 * balance at all.
 */
public class Vendor {

    private int     vendorId;
    private String  name;
    private String  contactPerson;
    private String  mobile;
    private String  email;
    private String  address;
    private String  gstin;
    private String  pan;
    private String  bankAccount;
    private String  bankIfsc;
    private String  category;
    private String  notes;
    private boolean active = true;
    private String  createdBy;
    private String  createdAt;

    // Derived - never stored.
    private BigDecimal ordered = Money.ZERO;   // value of live work orders
    private BigDecimal paid    = Money.ZERO;   // expenses booked against them
    private int        orderCount;
    private int        openOrders;             // issued but not fully paid

    /** What is still owed across every live work order for this vendor. */
    public BigDecimal getOutstanding() {
        BigDecimal left = Money.of(ordered).subtract(Money.of(paid));
        return left.signum() < 0 ? Money.ZERO : left;
    }

    /** True when more has been paid than was ever ordered - always worth seeing. */
    public boolean isOverPaid() {
        return Money.of(ordered).subtract(Money.of(paid)).signum() < 0;
    }

    public int getVendorId()                 { return vendorId; }
    public void setVendorId(int v)           { this.vendorId = v; }

    public String getName()                  { return name; }
    public void setName(String v)            { this.name = v; }

    public String getContactPerson()         { return contactPerson; }
    public void setContactPerson(String v)   { this.contactPerson = v; }

    public String getMobile()                { return mobile; }
    public void setMobile(String v)          { this.mobile = v; }

    public String getEmail()                 { return email; }
    public void setEmail(String v)           { this.email = v; }

    public String getAddress()               { return address; }
    public void setAddress(String v)         { this.address = v; }

    public String getGstin()                 { return gstin; }
    public void setGstin(String v)           { this.gstin = v; }

    public String getPan()                   { return pan; }
    public void setPan(String v)             { this.pan = v; }

    public String getBankAccount()           { return bankAccount; }
    public void setBankAccount(String v)     { this.bankAccount = v; }

    public String getBankIfsc()              { return bankIfsc; }
    public void setBankIfsc(String v)        { this.bankIfsc = v; }

    public String getCategory()              { return category; }
    public void setCategory(String v)        { this.category = v; }

    public String getNotes()                 { return notes; }
    public void setNotes(String v)           { this.notes = v; }

    public boolean isActive()                { return active; }
    public void setActive(boolean v)         { this.active = v; }

    public String getCreatedBy()             { return createdBy; }
    public void setCreatedBy(String v)       { this.createdBy = v; }

    public String getCreatedAt()             { return createdAt; }
    public void setCreatedAt(String v)       { this.createdAt = v; }

    public BigDecimal getOrdered()           { return ordered; }
    public void setOrdered(BigDecimal v)     { this.ordered = Money.of(v); }

    public BigDecimal getPaid()              { return paid; }
    public void setPaid(BigDecimal v)        { this.paid = Money.of(v); }

    public int getOrderCount()               { return orderCount; }
    public void setOrderCount(int v)         { this.orderCount = v; }

    public int getOpenOrders()               { return openOrders; }
    public void setOpenOrders(int v)         { this.openOrders = v; }
}
