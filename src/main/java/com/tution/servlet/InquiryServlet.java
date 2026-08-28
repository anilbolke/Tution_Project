package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.tution.dao.InquiryDAO;
import com.tution.model.Inquiry;

/**
 * Handles the public inquiry form POST. No login required — this is the
 * prospective-student Step 1 reachable from the login page.
 */
@WebServlet("/inquiry")
public class InquiryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final InquiryDAO inquiryDAO = new InquiryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.sendRedirect(req.getContextPath() + "/inquiry.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        Inquiry q = new Inquiry();
        q.setFullName(p(req, "fullName"));
        q.setMobile(p(req, "mobile"));
        q.setEmail(p(req, "email"));
        q.setClassInterest(p(req, "classInterest"));
        q.setSource(p(req, "source"));
        q.setMessage(p(req, "message"));

        String error = validate(req, q);
        if (error != null) {
            req.setAttribute("error", error);
            keep(req, q);
            req.getRequestDispatcher("/inquiry.jsp").forward(req, resp);
            return;
        }

        try {
            inquiryDAO.insert(q);
            req.setAttribute("success", Boolean.TRUE);
            req.setAttribute("savedName", q.getFullName());
            req.setAttribute("savedMobile", q.getMobile());
            req.setAttribute("savedClass", q.getClassInterest());
            req.getRequestDispatcher("/inquiry.jsp").forward(req, resp);
        } catch (SQLException e) {
            getServletContext().log("Inquiry save failed", e);
            req.setAttribute("error", "Could not submit your inquiry. Please try again.");
            keep(req, q);
            req.getRequestDispatcher("/inquiry.jsp").forward(req, resp);
        }
    }

    private String validate(HttpServletRequest req, Inquiry q) {
        if (isBlank(q.getFullName()))             return "Please enter your full name.";
        if (q.getMobile() == null || !q.getMobile().matches("[6-9]\\d{9}"))
                                                  return "Please enter a valid 10-digit mobile number.";
        if (!"on".equals(p(req, "agree")))        return "Please accept the Terms & Conditions to continue.";
        return null;
    }

    /** Keep entered values so the form can be re-rendered on error. */
    private void keep(HttpServletRequest req, Inquiry q) {
        req.setAttribute("fullName", q.getFullName());
        req.setAttribute("mobile", q.getMobile());
        req.setAttribute("email", q.getEmail());
        req.setAttribute("classInterest", q.getClassInterest());
        req.setAttribute("source", q.getSource());
        req.setAttribute("message", q.getMessage());
    }

    private static String p(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? null : v.trim();
    }
    private static boolean isBlank(String s) { return s == null || s.isEmpty(); }
}
