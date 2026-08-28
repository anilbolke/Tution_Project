package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.VendorDAO;
import com.tution.dao.WorkOrderDAO;
import com.tution.model.User;
import com.tution.model.Vendor;
import com.tution.model.WorkOrder;
import com.tution.service.WorkOrderService;
import com.tution.util.Money;
import com.tution.util.ReceiptPdf;

/**
 * Work orders: what has been committed to each vendor, and how much of it is paid.
 *
 * ADMIN only, matching {@code AuthFilter.ADMIN_ONLY} - this is where institute
 * money gets promised.
 */
@WebServlet("/work-orders")
public class WorkOrderServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final WorkOrderDAO     dao       = new WorkOrderDAO();
    private final VendorDAO        vendorDao = new VendorDAO();
    private final WorkOrderService service   = new WorkOrderService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireAdmin(req, resp);
        if (user == null) return;

        // A work order PDF short-circuits the page entirely.
        int pdfId = parseInt(req.getParameter("pdf"), 0);
        if (pdfId > 0) { streamOrder(pdfId, resp); return; }

        try {
            int    vendorId = parseInt(req.getParameter("vendor"), 0);
            String status   = trim(req.getParameter("status"));
            String pay      = trim(req.getParameter("pay"));
            String q        = trim(req.getParameter("q"));

            req.setAttribute("rows",     dao.list(vendorId, status, pay, q));
            req.setAttribute("totals",   dao.totals(vendorId, status));
            req.setAttribute("vendors",  vendorDao.list(null, false));
            req.setAttribute("vendorId", Integer.valueOf(vendorId));
            req.setAttribute("status",   status == null ? "" : status);
            req.setAttribute("pay",      pay    == null ? "" : pay);
            req.setAttribute("q",        q      == null ? "" : q);

            int id = parseInt(req.getParameter("id"), 0);
            if (id > 0) req.setAttribute("order", dao.findById(id));
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load work orders: " + e.getMessage());
            req.setAttribute("rows", new ArrayList<WorkOrder>());
        }
        req.getRequestDispatcher("/WEB-INF/views/work_orders.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireAdmin(req, resp);
        if (user == null) return;

        String action = orDefault(req.getParameter("action"), "");
        String back   = "/work-orders";

        try {
            switch (action) {
                case "save": {
                    WorkOrder w = service.save(
                            parseInt(req.getParameter("workOrderId"), 0),
                            parseInt(req.getParameter("vendorId"), 0),
                            req.getParameter("title"),
                            req.getParameter("description"),
                            req.getParameter("orderValue"),
                            req.getParameter("orderDate"),
                            req.getParameter("expectedDate"),
                            req.getParameter("remarks"),
                            user);
                    flash(req, w.getWoNo() + " saved for " + Money.rs(w.getOrderValue())
                             + (w.isDraft() ? ". It is a draft — issue it before anything "
                                            + "can be paid against it." : "."));
                    back = "/work-orders?id=" + w.getWorkOrderId();
                    break;
                }
                case "status": {
                    WorkOrder w = service.setStatus(
                            parseInt(req.getParameter("workOrderId"), 0),
                            orDefault(req.getParameter("to"), ""), user);
                    flash(req, service.statusMessage(w));
                    back = "/work-orders?id=" + w.getWorkOrderId();
                    break;
                }
                default:
                    flashError(req, "Unknown action.");
            }
        } catch (WorkOrderService.WorkOrderException e) {
            flashError(req, e.getMessage());
            int id = parseInt(req.getParameter("workOrderId"), 0);
            if (id > 0) back = "/work-orders?id=" + id;
        } catch (SQLException e) {
            flashError(req, "Could not save: " + e.getMessage());
        }
        resp.sendRedirect(req.getContextPath() + back);
    }

    /* ───────────────────────── the order as a PDF ───────────────────────── */

    /**
     * The work order as a document to send the vendor.
     *
     * ASCII ONLY - {@link ReceiptPdf} strips anything outside 32..126, so every
     * amount goes through {@link Money#rs} ("Rs. ") rather than the rupee sign,
     * which would vanish and leave a bare number on a document somebody is
     * expected to act on.
     */
    private void streamOrder(int workOrderId, HttpServletResponse resp) throws IOException {
        WorkOrder w;
        Vendor    v;
        try {
            w = dao.findById(workOrderId);
            if (w == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No such work order."); return; }
            v = vendorDao.findById(w.getVendorId());
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                           "Could not build the work order.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Vendor", w.getVendorName() });
        if (v != null) {
            if (v.getContactPerson() != null) rows.add(new String[] { "Contact", v.getContactPerson() });
            if (v.getMobile()  != null) rows.add(new String[] { "Mobile",  v.getMobile() });
            if (v.getGstin()   != null) rows.add(new String[] { "GSTIN",   v.getGstin() });
            if (v.getAddress() != null) rows.add(new String[] { "Address", v.getAddress() });
        }
        rows.add(new String[] { "-", "" });
        rows.add(new String[] { "Work",       w.getTitle() });
        if (w.getDescription() != null) rows.add(new String[] { "Details", w.getDescription() });
        rows.add(new String[] { "Order Date", w.getOrderDate() });
        if (w.getExpectedDate() != null) rows.add(new String[] { "Expected By", w.getExpectedDate() });
        rows.add(new String[] { "Status",     w.getStatusLabel() });
        rows.add(new String[] { "-", "" });
        rows.add(new String[] { "Order Value", Money.rs(w.getOrderValue()) });
        rows.add(new String[] { "Paid To Date", Money.rs(w.getPaid()) });
        rows.add(new String[] { "Balance",      Money.rs(w.getRemaining()) });
        if (w.getRemarks() != null) {
            rows.add(new String[] { "-", "" });
            rows.add(new String[] { "Remarks", w.getRemarks() });
        }
        if (w.getCreatedBy() != null) rows.add(new String[] { "Raised By", w.getCreatedBy() });

        byte[] pdf = ReceiptPdf.document(
                "Havellsson NEET Samrat",
                "Purchase / Work Order",
                "Work Order",
                "No: " + w.getWoNo(),
                rows.toArray(new String[0][]),
                w.isDraft()
                    ? "DRAFT - not yet issued. This is not an authorisation to proceed."
                    : "Please quote " + w.getWoNo() + " on every invoice against this order.");

        resp.setContentType("application/pdf");
        resp.setContentLength(pdf.length);
        resp.setHeader("Content-Disposition",
                       "inline; filename=\"" + w.getWoNo() + ".pdf\"");
        resp.getOutputStream().write(pdf);
    }

    /* ───────────────────────── helpers ───────────────────────── */

    private User requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = (User) req.getSession().getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return null;
        }
        if (!user.canSeeFinance()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                           "Work orders are managed by the administrator and the accountant.");
            return null;
        }
        return user;
    }

    private void flash(HttpServletRequest req, String msg) {
        req.getSession().setAttribute("flash", msg);
    }

    private void flashError(HttpServletRequest req, String msg) {
        req.getSession().setAttribute("flashError", msg);
    }

    private static String trim(String v) {
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    private static String orDefault(String v, String dflt) {
        return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
    }

    private static int parseInt(String v, int dflt) {
        try { return Integer.parseInt(v.trim()); }
        catch (RuntimeException e) { return dflt; }
    }
}
