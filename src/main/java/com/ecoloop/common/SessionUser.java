package com.ecoloop.common;

import com.ecoloop.identity.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

public record SessionUser(UUID id, String role) {

    public static SessionUser require() {
        return current().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public static SessionUser require(HttpServletRequest request) {
        return require();
    }

    public static Optional<SessionUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof UserPrincipal up) {
            return Optional.of(new SessionUser(up.getId(), up.getRole()));
        }
        return Optional.empty();
    }

    public void requireRole(String expected) {
        if (!expected.equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }
}
