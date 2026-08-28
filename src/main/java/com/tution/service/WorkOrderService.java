package com.tution.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import com.tution.dao.VendorDAO;
import com.tution.dao.WorkOrderDAO;
import com.tution.model.User;
import com.tution.model.Vendor;
import com.tution.model.WorkOrder;
import com.tution.util.Money;

/**
 * The rules around committing money to a vendor.
 *
 * A work order is a promise to pay, so the guards here are all about keeping that
 * promise honest once expenses start landing against it: the value cannot be cut
 * below what has already gone out, an order nobody approved cannot be paid, and
 * an order with money against it cannot be quietly cancelled.
 */
public class WorkOrderService {

    /** Thrown for something a person needs to fix; the message is shown as-is. */
    public static class WorkOrderException extends Exception {
        private static final long serialVersionUID = 1L;
        public WorkOrderException(String msg) { super(msg); }
    }

    private final WorkOrderDAO dao       = new WorkOrderDAO();
    private final VendorDAO    vendorDao = new VendorDAO();

    /** Raises a new work order, or edits the terms of an existing one. */
    public WorkOrder save(int workOrderId, int vendorId, String title, String description,
                          String rawValue, String orderDate, String expectedDate,
                          String remarks, User by)
            throws WorkOrderException, SQLException {

        if (blank(title)) {
            throw new WorkOrderException("A work order needs a title - what the money is for. "
                    + "\"OMR sheet printing - HAMSE Dec\" reads far better on a voucher "
                    + "six months from now than \"printing\".");
        }
        BigDecimal value = Money.parse(rawValue);
        if (value == null) {
            throw new WorkOrderException("\"" + (rawValue == null ? "" : rawValue.trim())
                                       + "\" is not a valid amount.");
        }
        if (value.signum() <= 0) {
            throw new WorkOrderException("A work order value must be more than zero.");
        }
        String on   = requireDate(orderDate, "order date");
        String want = blank(expectedDate) ? null : requireDate(expectedDate, "expected date");

        if (workOrderId > 0) {
            WorkOrder w = dao.findById(workOrderId);
            if (w == null) throw new WorkOrderException("That work order no longer exists.");
            if (w.isCancelled()) {
                throw new WorkOrderException(w.getWoNo() + " is cancelled and cannot be edited. "
                        + "Raise a new order if the work is going ahead after all.");
            }
            // Cutting the value below what has gone out would leave the order
            // permanently over-paid with no way to explain the difference.
            if (value.compareTo(Money.of(w.getPaid())) < 0) {
                throw new WorkOrderException(Money.rs(w.getPaid()) + " has already been paid "
                        + "against " + w.getWoNo() + ", so its value cannot be set below that. "
                        + "Cancel an expense first if the order really is smaller.");
            }
            w.setTitle(title.trim());
            w.setDescription(description);
            w.setOrderValue(value);
            w.setOrderDate(on);
            w.setExpectedDate(want);
            w.setRemarks(remarks);
            dao.update(w);
            return w;
        }

        Vendor v = vendorDao.findById(vendorId);
        if (v == null)     throw new WorkOrderException("Choose a vendor for this work order.");
        if (!v.isActive()) {
            throw new WorkOrderException(v.getName() + " is retired, so no new work can be "
                    + "ordered from them. Restore the vendor first if that is wrong.");
        }

        WorkOrder w = new WorkOrder();
        w.setVendorId(vendorId);
        w.setTitle(title.trim());
        w.setDescription(description);
        w.setOrderValue(value);
        w.setOrderDate(on);
        w.setExpectedDate(want);
        w.setRemarks(remarks);
        w.setStatus(WorkOrder.DRAFT);      // nothing is payable until somebody issues it
        if (by != null) {
            w.setCreatedBy(by.getFullName());
            w.setCreatedById(by.getUserId());
        }
        dao.insert(w);
        w.setVendorName(v.getName());
        return w;
    }

    /**
     * Moves an order along its lifecycle.
     *
     * Only the transitions below are allowed, and each refusal says why. The one
     * that carries real weight is CANCELLED: an order with live expenses against
     * it cannot be cancelled, because the money has already gone and cancelling
     * would strand it against a commitment the system says never happened.
     */
    public WorkOrder setStatus(int workOrderId, String to, User by)
            throws WorkOrderException, SQLException {

        WorkOrder w = dao.findById(workOrderId);
        if (w == null) throw new WorkOrderException("That work order no longer exists.");

        String from = w.getStatus();
        if (from.equals(to)) return w;

        if (WorkOrder.CANCELLED.equals(from)) {
            throw new WorkOrderException(w.getWoNo() + " is cancelled. A cancelled order "
                    + "stays cancelled - raise a new one instead.");
        }
        if (WorkOrder.CANCELLED.equals(to) && Money.of(w.getPaid()).signum() > 0) {
            throw new WorkOrderException("Cannot cancel " + w.getWoNo() + ": "
                    + Money.rs(w.getPaid()) + " has already been paid against it. "
                    + "Cancel those expenses first if the payment is being reversed.");
        }
        if (!allowed(from, to)) {
            throw new WorkOrderException("A " + label(from) + " work order cannot be moved "
                    + "straight to " + label(to) + ".");
        }

        dao.setStatus(workOrderId, to, by == null ? null : by.getUserId());
        w.setStatus(to);
        return w;
    }

    /** What the user is told after a status change - the payable rule is the point. */
    public String statusMessage(WorkOrder w) {
        switch (w.getStatus()) {
            case WorkOrder.ISSUED:
                return w.getWoNo() + " is issued. Expenses can now be booked against it, "
                     + "up to " + Money.rs(w.getOrderValue()) + ".";
            case WorkOrder.CANCELLED:
                return w.getWoNo() + " is cancelled. It no longer counts towards the vendor's "
                     + "ordered value, and nothing can be paid against it.";
            case WorkOrder.COMPLETED:
                return w.getWoNo() + " is marked completed. Any balance can still be paid.";
            default:
                return w.getWoNo() + " is now " + label(w.getStatus()).toLowerCase() + ".";
        }
    }

    /**
     * DRAFT is the only state nothing can be paid from, so leaving it is the
     * approval step and the only transition that stamps an approver.
     */
    private boolean allowed(String from, String to) {
        switch (from) {
            case WorkOrder.DRAFT:
                return WorkOrder.ISSUED.equals(to) || WorkOrder.CANCELLED.equals(to);
            case WorkOrder.ISSUED:
                return WorkOrder.IN_PROGRESS.equals(to) || WorkOrder.COMPLETED.equals(to)
                    || WorkOrder.CANCELLED.equals(to);
            case WorkOrder.IN_PROGRESS:
                return WorkOrder.COMPLETED.equals(to) || WorkOrder.CANCELLED.equals(to);
            case WorkOrder.COMPLETED:
                // Reopening is allowed: work signed off too early is common enough,
                // and the alternative is a second order for the same job.
                return WorkOrder.IN_PROGRESS.equals(to) || WorkOrder.CANCELLED.equals(to);
            default:
                return false;
        }
    }

    private String label(String status) {
        WorkOrder w = new WorkOrder();
        w.setStatus(status);
        return w.getStatusLabel();
    }

    private String requireDate(String date, String what) throws WorkOrderException {
        if (blank(date)) return LocalDate.now().toString();
        try {
            return LocalDate.parse(date.trim()).toString();
        } catch (RuntimeException e) {
            throw new WorkOrderException("\"" + date.trim() + "\" is not a valid " + what + ".");
        }
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
