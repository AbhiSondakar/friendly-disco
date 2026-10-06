package com.ecoloop.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Captures a small, bounded copy of mutating JSON requests for redacted audit metadata. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditRequestDetailsFilter extends OncePerRequestFilter {

    static final int MAX_AUDIT_BODY_BYTES = 16 * 1024;
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")
            || !MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(new ContentCachingRequestWrapper(request, MAX_AUDIT_BODY_BYTES), response);
    }
}
