package com.tution.util;

/**
 * Settings for the automated reminder run.
 *
 * {@link #AUTO_SEND} ships as false on purpose. The three templates below are
 * NOT yet approved by Meta, and a business-initiated WhatsApp message with an
 * unapproved template is rejected by the gateway — so auto-sending today would
 * produce a run of failures and no reminders. Everything else works now: the
 * queue is computed, shown on /reminders, and can be sent by hand.
 *
 * Flip AUTO_SEND to true once the templates come back approved. No other change
 * is needed — the scheduler is already wired and running.
 */
public class ReminderConfig {

    /** Master switch for the unattended daily send. */
    public static final boolean AUTO_SEND = false;

    /**
     * When true, nothing is actually sent: the queue is built and the run is
     * logged, but no request reaches the WhatsApp gateway and no reminder is
     * marked as sent.
     *
     * Defaults to TRUE so that testing, demos and a first deploy cannot message
     * real parents by accident — the numbers in a half-populated database are
     * usually placeholders, and one wrong send to a real family is not
     * recoverable. Set to false only once the templates are approved and the
     * data has been checked.
     */
    public static final boolean DRY_RUN = true;

    /** Hour of day (0-23, server time) the daily run fires. */
    public static final int RUN_HOUR = 9;
    public static final int RUN_MINUTE = 0;

    /**
     * How many days ahead a fee reminder goes out. Three days gives a parent
     * time to arrange money before the due date rather than after it.
     */
    public static final int FEE_LEAD_DAYS = 3;

    /** How many days ahead a demo reminder goes out. */
    public static final int DEMO_LEAD_DAYS = 1;

    // ── Templates (all verified APPROVED on the gateway) ──
    // Names, language and parameter COUNT must match the approved template
    // exactly, or the gateway rejects the send.

    /**
     * Fee due: a purpose-built approved template.
     * {{1}} amount · {{2}} student name · {{3}} due date · {{4}} what it is for.
     */
    public static final String TPL_FEE_DUE  = "fees_reminder";
    public static final String LANG_FEE_DUE = "en";

    /**
     * Follow-up and demo reminders have no dedicated template, so they use the
     * institute's approved GENERIC one-liner: {{1}} name · {{2}} the message.
     *
     * This is why neither is blocked on a new Meta approval — the generic
     * template already covers any scenario we have no specific wording for.
     */
    public static final String TPL_GENERIC      = "com_1_line";
    public static final String LANG_GENERIC     = "en";

    /** Alternative generic with two message lines and no name slot. */
    public static final String TPL_GENERIC_2LINE  = "com_2_line_new";
    public static final String LANG_GENERIC_2LINE = "en";

    /** Follow-up nudge — sent through the generic template. */
    public static final String TPL_FOLLOWUP  = TPL_GENERIC;
    public static final String LANG_FOLLOWUP = LANG_GENERIC;

    /** Demo nudge — sent through the generic template. */
    public static final String TPL_DEMO  = TPL_GENERIC;
    public static final String LANG_DEMO = LANG_GENERIC;

    private ReminderConfig() { }
}
