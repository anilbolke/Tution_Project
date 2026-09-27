package com.tution.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tution.dao.SearchDAO;
import com.tution.model.SearchHit;
import com.tution.model.User;

/**
 * Global search (Sales Module Requirement 1).
 *
 * One box, searched against leads AND admitted students at once. When nothing
 * matches, the view offers "Create New Enquiry" pre-filled with whatever was
 * typed — that hand-off is the point of the screen, not a nicety.
 */
@WebServlet("/search")
public class GlobalSearchServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final SearchDAO searchDAO = new SearchDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        User user = (session == null) ? null : (User) session.getAttribute("user");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }

        String q = req.getParameter("q");
        req.setAttribute("q", q == null ? "" : q.trim());

        if (q != null && !q.trim().isEmpty()) {
            // A counsellor searches only their own leads; ADMIN and back-office
            // staff search everything.
            Integer scope = com.tution.dao.Scope.of(user);
            try {
                List<SearchHit> hits = searchDAO.search(q, scope);
                req.setAttribute("hits", hits);
                req.setAttribute("searched", Boolean.TRUE);
                // Pre-fill hints for the "create new enquiry" hand-off.
                if (SearchDAO.isPhone(q.trim())) {
                    req.setAttribute("prefillMobile", SearchDAO.last10(q.trim()));
                } else {
                    req.setAttribute("prefillName", q.trim());
                }
            } catch (SQLException e) {
                getServletContext().log("Global search failed for '" + q + "'", e);
                req.setAttribute("error", "Search failed. Please try again.");
            }
        }
        req.getRequestDispatcher("/WEB-INF/views/search.jsp").forward(req, resp);
    }
}
