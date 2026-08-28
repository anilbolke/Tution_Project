package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.VendorDAO;
import com.tution.dao.WorkOrderDAO;
import com.tution.model.User;
import com.tution.model.Vendor;
import com.tution.service.VendorService;

/**
 * Vendors: who the tuition pays, and what each one is owed.
 *
 * ADMIN only, matching {@code AuthFilter.ADMIN_ONLY}. Unlike an exam fee - which
 * is front-desk work - a vendor record decides where institute money goes, so it
 * stays with management.
 */
@WebServlet("/vendors")
public class VendorServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final VendorDAO     dao     = new VendorDAO();
    private final WorkOrderDAO  woDao   = new WorkOrderDAO();
    private final VendorService service = new VendorService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireAdmin(req, resp);
        if (user == null) return;

        try {
            String q       = trim(req.getParameter("q"));
            boolean allToo = req.getParameter("all") != null;

            req.setAttribute("rows",       dao.list(q, !allToo));
            req.setAttribute("categories", dao.categories());
            req.setAttribute("q",          q == null ? "" : q);
            req.setAttribute("showAll",    Boolean.valueOf(allToo));

            // ?id= opens one vendor with their work orders alongside.
            int id = parseInt(req.getParameter("id"), 0);
            if (id > 0) {
                Vendor v = dao.findById(id);
                req.setAttribute("vendor", v);
                if (v != null) req.setAttribute("orders", woDao.list(id, null, null, null));
            }
        } catch (SQLException e) {
            req.setAttribute("error", "Could not load vendors: " + e.getMessage());
            req.setAttribute("rows", new ArrayList<Vendor>());
        }
        req.getRequestDispatcher("/WEB-INF/views/vendors.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        User user = requireAdmin(req, resp);
        if (user == null) return;

        String action = orDefault(req.getParameter("action"), "");
        String back   = "/vendors";

        try {
            switch (action) {
                case "save": {
                    Vendor v = service.save(
                            parseInt(req.getParameter("vendorId"), 0),
                            req.getParameter("name"),
                            req.getParameter("contactPerson"),
                            req.getParameter("mobile"),
                            req.getParameter("email"),
                            req.getParameter("address"),
                            req.getParameter("gstin"),
                            req.getParameter("pan"),
                            req.getParameter("bankAccount"),
                            req.getParameter("bankIfsc"),
                            req.getParameter("category"),
                            req.getParameter("notes"),
                            user);
                    flash(req, v.getName() + " saved.");
                    back = "/vendors?id=" + v.getVendorId();
                    break;
                }
                case "retire": {
                    Vendor v = service.setActive(parseInt(req.getParameter("vendorId"), 0), false);
                    flash(req, service.retireMessage(v));
                    break;
                }
                case "restore": {
                    Vendor v = service.setActive(parseInt(req.getParameter("vendorId"), 0), true);
                    flash(req, v.getName() + " is active again and can take new work orders.");
                    back = "/vendors?id=" + v.getVendorId();
                    break;
                }
                default:
                    flashError(req, "Unknown action.");
            }
        } catch (VendorService.VendorException e) {
            flashError(req, e.getMessage());
            int id = parseInt(req.getParameter("vendorId"), 0);
            if (id > 0) back = "/vendors?id=" + id;
        } catch (SQLException e) {
            flashError(req, "Could not save: " + e.getMessage());
        }
        resp.sendRedirect(req.getContextPath() + back);
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
                           "Vendors are managed by the administrator and the accountant.");
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
