package com.tution.model;

/**
 * Sales figures for one counsellor, or for the whole institute when
 * {@code counsellorId} is null.
 *
 * Kept separate from {@link Stats} on purpose: Stats is the institute-wide
 * operational dashboard that already works, and widening it risked changing
 * figures the client already checks daily.
 */
public class SalesStats {

    public Integer counsellorId;      // null = institute-wide
    public String  counsellorName;

    // ── pipeline ──
    public int leadsTotal;            // open leads owned
    public int leadsNew;
    public int leadsContacted;
    public int leadsInterested;
    public int leadsDemo;             // DEMO_PENDING + DEMO_COMPLETED
    public int leadsFollowup;
    public int leadsLost;             // NOT_INTERESTED + LOST

    // ── work queue ──
    public int followupsToday;
    public int followupsOverdue;
    public int demosToday;
    public int demosUpcoming;         // next 7 days

    // ── month to date ──
    public int  leadsMtd;             // enquiries received this month
    public int  conversionsMtd;       // leads converted this month
    public long revenueMtd;           // fees collected this month
    public long pendingFees;          // outstanding on this counsellor's students

    /**
     * Conversions as a percentage of leads received this month.
     *
     * Deliberately month-on-month rather than lifetime: a counsellor's current
     * performance is what the leaderboard is for, and a lifetime ratio would
     * flatter whoever has been there longest.
     */
    public int conversionRate() {
        if (leadsMtd <= 0) {
            return 0;
        }
        return (int) Math.round(conversionsMtd * 100.0 / leadsMtd);
    }

    /** Total attention needed right now — drives the red badge on the tile. */
    public int actionsDue() {
        return followupsToday + followupsOverdue + demosToday;
    }
}
