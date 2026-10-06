package com.ecoloop.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ActiveUserAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ActiveUserAuthenticationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String email = auth.getName();
            var userOpt = userRepository.findByEmailIgnoreCase(User.normalizeEmail(email));

            if (userOpt.isEmpty() || !userOpt.get().isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"User account is inactive or not found\"}");
                return;
            }

            User user = userOpt.get();
            String authoritativeRole = "ROLE_" + user.getRole();
            boolean roleMatches = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(authoritativeRole));

            if (!roleMatches || !(auth.getPrincipal() instanceof UserPrincipal up && up.getId().equals(user.getId()))) {
                UserPrincipal updatedPrincipal = UserPrincipal.from(user);
                UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                    updatedPrincipal,
                    auth.getCredentials(),
                    updatedPrincipal.getAuthorities()
                );
                newAuth.setDetails(auth.getDetails());
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
