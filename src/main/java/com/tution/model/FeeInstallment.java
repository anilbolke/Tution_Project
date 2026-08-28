package com.tution.model;

/**
 * One row of a student's payment schedule.
 *
 * The schedule used to exist only as JavaScript in admission.jsp — rendered as a
 * preview and thrown away on submit. Persisting it is what makes due-date
 * reminders, ageing and the pending-fee report possible at all.
 */
public class FeeInstallment {

    private int    installmentId;
    private int    studentId;
    private int    seq;
    private String kind = "COURSE";   // COURSE or REGISTRATION
    private Double pct;               // part-payment % this slice carries
    private String label;
    private int    amount;
    private String dueDate;
    private int    paidAmount;
    private String paidDate;
    private String status;        // PENDING / PARTIAL / PAID / OVERDUE
    private boolean reminderSent;

    // joined for the reminder queue and the pending-fee report
    private String studentName;
    private String admissionNo;
    private String studentMobile;
    private String parentMobile;

    public int getBalance() {
        return Math.max(0, amount - paidAmount);
    }

    public boolean isSettled() {
        return "PAID".equalsIgnoreCase(status);
    }

    /** True when money is still due and the date has passed. */
    public boolean isOverdue(String today) {
        return !isSettled() && dueDate != null && dueDate.compareTo(today) < 0;
    }

    /**
     * Days past the due date, negative when still in the future. Used for the
     * 30/60/90 ageing buckets on the defaulter report.
     */
    public long daysOverdue(String today) {
        if (dueDate == null) {
            return 0;
        }
        try {
            return java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(dueDate), java.time.LocalDate.parse(today));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Status recomputed from the money actually allocated to this installment. */
    public String derivedStatus(String today) {
        if (paidAmount >= amount)          return "PAID";
        if (isOverdue(today))              return "OVERDUE";
        if (paidAmount > 0)                return "PARTIAL";
        return "PENDING";
    }

    /** True for the non-refundable registration charge collected at admission. */
    public boolean isRegistration() { return "REGISTRATION".equalsIgnoreCase(kind); }

    public String getKind()                 { return kind; }
    public void   setKind(String v)         { this.kind = v; }

    public Double getPct()                  { return pct; }
    public void   setPct(Double v)          { this.pct = v; }

    public int  getInstallmentId()          { return installmentId; }
    public void setInstallmentId(int v)     { this.installmentId = v; }

    public int  getStudentId()              { return studentId; }
    public void setStudentId(int v)         { this.studentId = v; }

    public int  getSeq()                    { return seq; }
    public void setSeq(int v)               { this.seq = v; }

    public String getLabel()                { return label; }
    public void   setLabel(String v)        { this.label = v; }

    public int  getAmount()                 { return amount; }
    public void setAmount(int v)            { this.amount = v; }

    public String getDueDate()              { return dueDate; }
    public void   setDueDate(String v)      { this.dueDate = v; }

    public int  getPaidAmount()             { return paidAmount; }
    public void setPaidAmount(int v)        { this.paidAmount = v; }

    public String getPaidDate()             { return paidDate; }
    public void   setPaidDate(String v)     { this.paidDate = v; }

    public String getStatus()               { return status; }
    public void   setStatus(String v)       { this.status = v; }

    public boolean isReminderSent()         { return reminderSent; }
    public void    setReminderSent(boolean v) { this.reminderSent = v; }

    public String getStudentName()          { return studentName; }
    public void   setStudentName(String v)  { this.studentName = v; }

    public String getAdmissionNo()          { return admissionNo; }
    public void   setAdmissionNo(String v)  { this.admissionNo = v; }

    public String getStudentMobile()        { return studentMobile; }
    public void   setStudentMobile(String v) { this.studentMobile = v; }

    public String getParentMobile()         { return parentMobile; }
    public void   setParentMobile(String v) { this.parentMobile = v; }
}
