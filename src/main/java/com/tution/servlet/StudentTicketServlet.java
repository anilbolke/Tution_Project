package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.TicketDAO;
import com.tution.model.Student;
import com.tution.model.Ticket;
import com.tution.model.TicketReply;

/** Student portal: raise concerns (tickets) and follow up on them. */
@WebServlet("/student-tickets")
public class StudentTicketServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final TicketDAO ticketDAO = new TicketDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Student s = student(req);
        if (s == null) { resp.sendRedirect(req.getContextPath() + "/student-login.jsp"); return; }

        Integer id = parseInt(req.getParameter("id"));
        try {
            if (id != null) {
                Ticket t = ticketDAO.findById(id);
                if (t == null || t.getStudentId() != s.getStudentId()) {   // own tickets only
                    resp.sendRedirect(req.getContextPath() + "/student-tickets");
                    return;
                }
                req.setAttribute("ticket", t);
                req.setAttribute("replies", ticketDAO.findReplies(id));
                req.getRequestDispatcher("/WEB-INF/views/student_ticket_view.jsp").forward(req, resp);
                return;
            }
            req.setAttribute("tickets", ticketDAO.findByStudent(s.getStudentId()));
        } catch (SQLException e) {
            getServletContext().log("Student tickets load failed", e);
            req.setAttribute("error", "Could not load your tickets. Please try again.");
        }
        req.getRequestDispatcher("/WEB-INF/views/student_tickets.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Student s = student(req);
        if (s == null) { resp.sendRedirect(req.getContextPath() + "/student-login.jsp"); return; }

        String action = trim(req.getParameter("action"));
        String ctx = req.getContextPath();
        try {
            if ("reply".equals(action)) {
                Integer ticketId = parseInt(req.getParameter("ticketId"));
                String  msg = trim(req.getParameter("message"));
                if (ticketId != null && !msg.isEmpty()) {
                    Ticket t = ticketDAO.findById(ticketId);
                    if (t != null && t.getStudentId() == s.getStudentId()) {
                        TicketReply r = new TicketReply();
                        r.setTicketId(ticketId);
                        r.setSender("STUDENT");
                        r.setSenderName(s.getFullName());
                        r.setMessage(msg);
                        ticketDAO.addReply(r);
                        if ("RESOLVED".equals(t.getStatus()) || "CLOSED".equals(t.getStatus())) {
                            ticketDAO.updateStatus(ticketId, "OPEN");   // re-open on a fresh reply
                        }
                    }
                }
                resp.sendRedirect(ctx + "/student-tickets?id=" + ticketId);
                return;
            }

            // default: create a new ticket
            String subject  = trim(req.getParameter("subject"));
            String message  = trim(req.getParameter("message"));
            String category = trim(req.getParameter("category"));
            String priority = trim(req.getParameter("priority"));
            if (subject.isEmpty() || message.isEmpty()) {
                req.setAttribute("error", "Please enter a subject and describe your concern.");
                req.setAttribute("formSubject", subject);
                req.setAttribute("formMessage", message);
                req.setAttribute("tickets", ticketDAO.findByStudent(s.getStudentId()));
                req.getRequestDispatcher("/WEB-INF/views/student_tickets.jsp").forward(req, resp);
                return;
            }
            Ticket t = new Ticket();
            t.setStudentId(s.getStudentId());
            t.setSubject(subject);
            t.setMessage(message);
            t.setCategory(category);
            t.setPriority(priority);
            int id = ticketDAO.insert(t);
            resp.sendRedirect(ctx + "/student-tickets?id=" + id + "&created=1");

        } catch (SQLException e) {
            getServletContext().log("Student ticket action failed", e);
            req.setAttribute("error", "Something went wrong. Please try again.");
            req.getRequestDispatcher("/WEB-INF/views/student_tickets.jsp").forward(req, resp);
        }
    }

    private Student student(HttpServletRequest req) {
        HttpSession sn = req.getSession(false);
        return (sn == null) ? null : (Student) sn.getAttribute("student");
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static Integer parseInt(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
