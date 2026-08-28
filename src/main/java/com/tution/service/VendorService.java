package com.tution.service;

import java.sql.SQLException;

import com.tution.dao.VendorDAO;
import com.tution.model.User;
import com.tution.model.Vendor;
import com.tution.util.Money;

/**
 * The rules around who the tuition pays.
 *
 * A vendor record is mostly clerical, with one thing that matters: the name is
 * the handle every work order and expense is read by, so a duplicate ("Shree
 * Printers" and "Shree printers ") splits one supplier's history into two and
 * makes the outstanding figure meaningless.
 */
public class VendorService {

    /** Thrown for something a person needs to fix; the message is shown as-is. */
    public static class VendorException extends Exception {
        private static final long serialVersionUID = 1L;
        public VendorException(String msg) { super(msg); }
    }

    private final VendorDAO dao = new VendorDAO();

    public Vendor save(int vendorId, String name, String contact, String mobile, String email,
                       String address, String gstin, String pan, String bankAccount,
                       String bankIfsc, String category, String notes, User by)
            throws VendorException, SQLException {

        if (blank(name)) throw new VendorException("A vendor needs a name.");
        String clean = name.trim();

        Vendor existing = dao.findByName(clean);
        if (existing != null && existing.getVendorId() != vendorId) {
            throw new VendorException("There is already a vendor called \"" + clean
                    + "\". Open that one instead - keeping two records for the same "
                    + "supplier splits their work orders and their outstanding balance.");
        }

        requireMobile(mobile);
        requireLength(gstin, 15, "GSTIN");
        requireLength(pan,   10, "PAN");

        Vendor v = vendorId > 0 ? dao.findById(vendorId) : new Vendor();
        if (v == null) throw new VendorException("That vendor no longer exists.");

        v.setVendorId(vendorId);
        v.setName(clean);
        v.setContactPerson(contact);
        v.setMobile(mobile);
        v.setEmail(email);
        v.setAddress(address);
        v.setGstin(gstin == null ? null : gstin.trim().toUpperCase());
        v.setPan(pan == null ? null : pan.trim().toUpperCase());
        v.setBankAccount(bankAccount);
        v.setBankIfsc(bankIfsc == null ? null : bankIfsc.trim().toUpperCase());
        v.setCategory(category);
        v.setNotes(notes);

        if (vendorId > 0) {
            dao.update(v);
        } else {
            if (by != null) v.setCreatedBy(by.getFullName());
            dao.insert(v);
        }
        return v;
    }

    /**
     * Retires a vendor, or brings one back.
     *
     * Deactivating is ALLOWED even with money still owed - stopping new orders
     * while old ones are settled is a normal thing to want. The caller is told
     * the outstanding figure so it is a decision rather than an accident.
     */
    public Vendor setActive(int vendorId, boolean active) throws VendorException, SQLException {
        Vendor v = dao.findById(vendorId);
        if (v == null) throw new VendorException("That vendor no longer exists.");
        dao.setActive(vendorId, active);
        v.setActive(active);
        return v;
    }

    /** The sentence shown after retiring a vendor, which names any money still owed. */
    public String retireMessage(Vendor v) {
        if (v.getOutstanding().signum() > 0) {
            return v.getName() + " is retired and will not appear on new work orders. "
                 + Money.rs(v.getOutstanding()) + " is still owed on their existing orders, "
                 + "and those can still be paid.";
        }
        return v.getName() + " is retired and will not appear on new work orders.";
    }

    private void requireMobile(String mobile) throws VendorException {
        if (blank(mobile)) return;
        String m = mobile.trim();
        if (!m.matches("[6-9]\\d{9}")) {
            throw new VendorException("\"" + m + "\" is not a valid 10-digit mobile number.");
        }
    }

    private void requireLength(String v, int len, String what) throws VendorException {
        if (blank(v)) return;
        if (v.trim().length() != len) {
            throw new VendorException("A " + what + " is " + len + " characters. \""
                                    + v.trim() + "\" is " + v.trim().length() + ".");
        }
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
