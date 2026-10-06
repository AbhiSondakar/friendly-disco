package com.ecoloop.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_EXPIRY = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final SessionRevocationService sessionRevocationService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${ecoloop.mail.from:noreply@ecoloop.local}")
    private String mailFrom;

    @Value("${ecoloop.mail.reset-base-url:http://localhost:5173/reset-password}")
    private String resetBaseUrl;

    public PasswordResetService(UserRepository users,
                                PasswordResetTokenRepository tokens,
                                PasswordEncoder encoder,
                                SessionRevocationService sessionRevocationService,
                                ObjectProvider<JavaMailSender> mailSenderProvider,
                                PlatformTransactionManager transactionManager) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.sessionRevocationService = sessionRevocationService;
        this.mailSenderProvider = mailSenderProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void requestPasswordReset(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            log.info("Password reset requested with blank or null email input");
            return;
        }

        String normalized = User.normalizeEmail(rawEmail);
        Optional<User> userOpt = users.findByEmailIgnoreCase(normalized);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unrecognized email lookup");
            return;
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            log.info("Password reset requested for inactive account");
            return;
        }

        String selector = UUID.randomUUID().toString();
        String verifier = generateVerifier();
        String rawToken = selector + "." + verifier;
        String tokenHash = encoder.encode(verifier);

        transactionTemplate.executeWithoutResult(status -> {
            tokens.deleteAllByUserId(user.getId());

            PasswordResetToken resetToken = new PasswordResetToken(
                user.getId(),
                selector,
                tokenHash,
                Instant.now().plus(TOKEN_EXPIRY)
            );
            tokens.save(resetToken);
        });

        sendResetEmail(user.getEmail(), rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }

        PasswordPolicy.validate(newPassword);

        ResetTokenParts tokenParts = parseToken(rawToken.trim());
        PasswordResetToken token = tokens.findBySelectorForUpdate(tokenParts.selector())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        if (token.isUsed() || token.isExpired() || !encoder.matches(tokenParts.verifier(), token.getTokenHash())) {
            throw new IllegalArgumentException("Invalid or expired password reset token");
        }

        User user = users.findById(token.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User account not found"));

        user.setPasswordHash(encoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        users.save(user);

        token.setUsedAt(Instant.now());
        tokens.save(token);

        sessionRevocationService.revokeAllUserSessions(user.getEmail());
        log.info("Password reset successfully completed for user: {}", user.getId());
    }

    private void sendResetEmail(String toEmail, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Reset email not dispatched.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(toEmail);
            message.setSubject("EcoLoop Password Reset Request");
            String resetLink = resetBaseUrl + "?token=" + rawToken;
            message.setText("Hello,\n\n"
                + "A password reset request was received for your EcoLoop account.\n"
                + "Click the link below or enter the reset token in the app to reset your password:\n\n"
                + resetLink + "\n\n"
                + "Token: " + rawToken + "\n\n"
                + "This token will expire in " + TOKEN_EXPIRY.toMinutes() + " minutes.\n"
                + "If you did not request this, please ignore this email.\n\n"
                + "The EcoLoop Team");
            mailSender.send(message);
            log.info("Password reset email sent to recipient");
        } catch (Exception e) {
            log.warn("Failed to send password reset email: {}", e.getMessage());
        }
    }

    private String generateVerifier() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private ResetTokenParts parseToken(String rawToken) {
        int separator = rawToken.indexOf('.');
        if (separator <= 0 || separator != rawToken.lastIndexOf('.') || separator == rawToken.length() - 1) {
            throw new IllegalArgumentException("Invalid or expired password reset token");
        }

        String selector = rawToken.substring(0, separator);
        try {
            if (!UUID.fromString(selector).toString().equals(selector)) {
                throw new IllegalArgumentException("Invalid or expired password reset token");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid or expired password reset token");
        }

        return new ResetTokenParts(selector, rawToken.substring(separator + 1));
    }

    private record ResetTokenParts(String selector, String verifier) {}
}
