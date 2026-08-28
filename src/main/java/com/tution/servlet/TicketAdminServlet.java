package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.TicketDAO;
import com.tution.model.Ticket;
import com.tution.model.TicketReply;
import com.tution.model.User;

/** Staff/management: view student tickets, reply, and change status. */
@WebServlet("/manage-tickets")
public class TicketAdminServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final List<String> STATUSES = Arrays.asList("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private final TicketDAO ticketDAO = new TicketDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;

        Integer id = parseInt(req.getParameter("id"));
        try {
            if (id != null) {
                Ticket t = ticketDAO.findById(id);
                if (t == null) { resp.sendRedirect(req.getContextPath() + "/manage-tickets"); return; }
                req.setAttribute("ticket", t);
                req.setAttribute("replies", ticketDAO.findReplies(id));
                req.getRequestDispatcher("/WEB-INF/views/ticket_admin_view.jsp").forward(req, resp);
                return;
            }
            req.setAttribute("tickets", ticketDAO.findAll());
        } catch (SQLException e) {
            getServletContext().log("Tickets load failed", e);
            req.setAttribute("error", "Could not load tickets. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/tickets_admin.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (guard(req, resp)) return;
        User user = (User) req.getSession().getAttribute("user");

        Integer ticketId = parseInt(req.getParameter("ticketId"));
        String  action   = trim(req.getParameter("action"));
        String  ctx = req.getContextPath();
        if (ticketId == null) { resp.sendRedirect(ctx + "/manage-tickets"); return; }

        try {
            if ("status".equals(action)) {
                String status = trim(req.getParameter("status"));
                if (STATUSES.contains(status)) ticketDAO.updateStatus(ticketId, status);
            } else if ("reply".equals(action)) {
                String msg = trim(req.getParameter("message"));
                if (!msg.isEmpty()) {
                    TicketReply r = new TicketReply();
                    r.setTicketId(ticketId);
                    r.setSender("STAFF");
                    r.setSenderName(user == null ? "Management" : user.getFullName());
                    r.setMessage(msg);
                    ticketDAO.addReply(r);
                    Ticket t = ticketDAO.findById(ticketId);
                    if (t != null && "OPEN".equals(t.getStatus())) ticketDAO.updateStatus(ticketId, "IN_PROGRESS");
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Ticket admin action failed", e);
        }
        resp.sendRedirect(ctx + "/manage-tickets?id=" + ticketId);
    }

    private boolean guard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("user") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return true;
        }
        return false;
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
