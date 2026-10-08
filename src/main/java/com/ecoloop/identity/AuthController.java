package com.ecoloop.identity;

import com.ecoloop.common.RateLimiterService;
import com.ecoloop.common.security.Role;
import com.ecoloop.common.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authManager;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final IdentityService identityApi;
    private final RateLimiterService rateLimiter;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    @Value("${ecoloop.security.trusted-proxies:}")
    private String trustedProxies;

    public AuthController(AuthenticationManager authManager,
                          UserRepository users,
                          PasswordEncoder encoder,
                          IdentityService identityApi,
                          RateLimiterService rateLimiter,
                          PasswordResetService passwordResetService,
                          EmailVerificationService emailVerificationService) {
        this.authManager = authManager;
        this.users = users;
        this.encoder = encoder;
        this.identityApi = identityApi;
        this.rateLimiter = rateLimiter;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank String name,
        String phone,
        String address) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityService.UserDto register(@Valid @RequestBody RegisterRequest req,
                                            HttpServletRequest httpReq,
                                            HttpServletResponse httpResp) {
        String ip = clientIp(httpReq);
        rateLimiter.checkLimit("register-ip", ip, 5, Duration.ofMinutes(1));

        String normalizedEmail = User.normalizeEmail(req.email());
        PasswordPolicy.validate(req.password());

        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(normalizedEmail);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setName(req.name().trim());
        if (req.phone() != null) u.setPhone(req.phone().trim());
        if (req.address() != null) u.setAddress(req.address().trim());
        u.setRole(Role.HOUSEHOLD.name());
        u.setActive(true);
        u.setEmailVerified(false);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        users.save(u);

        emailVerificationService.sendVerificationEmail(u);

        log.info("User registered successfully: userId={} role=HOUSEHOLD", u.getId());
        establishSession(normalizedEmail, req.password(), httpReq);
        return identityApi.toDto(u);
    }

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {}

    @PostMapping("/login")
    public ResponseEntity<IdentityService.UserDto> login(@Valid @RequestBody LoginRequest req,
                                                         HttpServletRequest httpReq,
                                                         HttpServletResponse httpResp) {
        String ip = clientIp(httpReq);
        String normalizedEmail = User.normalizeEmail(req.email());

        rateLimiter.checkLimit("login-ip", ip, 10, Duration.ofMinutes(1));
        rateLimiter.checkLoginLockout(normalizedEmail);

        Authentication auth;
        try {
            auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, req.password()));
        } catch (org.springframework.security.core.AuthenticationException ex) {
            rateLimiter.recordLoginFailure(normalizedEmail);
            throw ex;
        }

        rateLimiter.resetLoginFailures(normalizedEmail);

        User u = users.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!u.isActive() || u.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }

        saveSession(auth, httpReq);
        log.info("Login successful: userId={} role={}", u.getId(), u.getRole());
        return ResponseEntity.ok(identityApi.toDto(u));
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        if (token == null) {
            return Map.of("token", "", "headerName", "X-XSRF-TOKEN", "parameterName", "_csrf");
        }
        return Map.of(
            "token", token.getToken(),
            "headerName", token.getHeaderName(),
            "parameterName", token.getParameterName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        SecurityContextHolder.clearContext();
        log.info("User session invalidated on logout");
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@RequestBody Map<String, String> body, HttpServletRequest httpReq) {
        String ip = clientIp(httpReq);
        rateLimiter.checkLimit("forgot-ip", ip, 5, Duration.ofMinutes(1));

        String rawEmail = body.get("email");
        if (rawEmail != null && !rawEmail.isBlank()) {
            passwordResetService.requestPasswordReset(rawEmail);
        }
        log.info("Password reset request processed");
    }

    public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 12, max = 128) String newPassword) {}

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.token(), req.newPassword());
    }

    public record VerifyEmailRequest(
        @NotBlank String token) {}

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        emailVerificationService.verifyEmail(req.token());
        return Map.of("status", "success", "message", "Email verified successfully");
    }

    public record ResendVerificationRequest(
        @NotBlank @Email String email) {}

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest req, HttpServletRequest httpReq) {
        String ip = clientIp(httpReq);
        rateLimiter.checkLimit("resend-verification-ip", ip, 5, Duration.ofMinutes(1));
        emailVerificationService.resendVerification(req.email());
    }

    @GetMapping("/me")
    public IdentityService.UserDto me(HttpServletRequest r) {
        SessionUser user = SessionUser.require(r);
        return identityApi.findById(user.id());
    }

    private void establishSession(String email, String password, HttpServletRequest httpReq) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password));
        saveSession(auth, httpReq);
    }

    private void saveSession(Authentication auth, HttpServletRequest httpReq) {
        HttpSession oldSession = httpReq.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpReq.getSession(true);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }

    private String clientIp(HttpServletRequest req) {
        String remoteAddress = normalizeIpLiteral(req.getRemoteAddr()).orElse("unknown");
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            Optional<String> hop = normalizeIpLiteral(hops[i]);
            if (hop.isPresent() && !isTrustedProxy(hop.get())) {
                return hop.get();
            }
        }
        return remoteAddress;
    }

    private boolean isTrustedProxy(String address) {
        if (trustedProxies == null || trustedProxies.isBlank()) {
            return false;
        }
        return Arrays.stream(trustedProxies.split(","))
            .map(this::normalizeIpLiteral)
            .flatMap(Optional::stream)
            .anyMatch(address::equals);
    }

    private Optional<String> normalizeIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String candidate = value.trim();
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (!isIpLiteral(candidate)) {
            return Optional.empty();
        }

        try {
            return Optional.of(InetAddress.getByName(candidate).getHostAddress());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean isIpLiteral(String value) {
        if (value.contains(":")) {
            return value.matches("[0-9a-fA-F:.]+") && value.matches(".*[0-9a-fA-F].*");
        }

        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (!octet.matches("\\d{1,3}")) {
                return false;
            }
            try {
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }
}
