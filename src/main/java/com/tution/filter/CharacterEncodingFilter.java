package com.tution.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;

/**
 * Forces UTF-8 decoding of request parameters and UTF-8 response encoding.
 * Without this, non-ASCII input (e.g. the en-dash in class names, accented
 * names) is decoded as Latin-1 and stored as mojibake ("â€“").
 * Mapped to every request so it runs before any servlet reads parameters.
 */
@WebFilter("/*")
public class CharacterEncodingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        chain.doFilter(request, response);
    }
}
