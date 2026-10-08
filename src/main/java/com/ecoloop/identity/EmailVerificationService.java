package com.ecoloop.identity;

import com.ecoloop.common.DomainException;
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
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration TOKEN_EXPIRY = Duration.ofHours(24);

    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${ecoloop.mail.from:noreply@ecoloop.local}")
    private String mailFrom;

    @Value("${ecoloop.mail.verify-base-url:http://localhost:5173/verify-email}")
    private String verifyBaseUrl;

    public EmailVerificationService(UserRepository users,
                                    EmailVerificationTokenRepository tokens,
                                    PasswordEncoder encoder,
                                    ObjectProvider<JavaMailSender> mailSenderProvider,
                                    PlatformTransactionManager transactionManager) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailSenderProvider = mailSenderProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void sendVerificationEmail(User user) {
        if (user == null || user.isEmailVerified()) {
            return;
        }

        String selector = UUID.randomUUID().toString();
        String verifier = generateVerifier();
        String rawToken = selector + "." + verifier;
        String tokenHash = encoder.encode(verifier);

        transactionTemplate.executeWithoutResult(status -> {
            tokens.deleteAllByUserId(user.getId());

            EmailVerificationToken verificationToken = new EmailVerificationToken(
                user.getId(),
                selector,
                tokenHash,
                Instant.now().plus(TOKEN_EXPIRY)
            );
            tokens.save(verificationToken);
        });

        sendVerificationEmailMessage(user.getEmail(), rawToken);
    }

    public void resendVerification(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return;
        }

        String normalized = User.normalizeEmail(rawEmail);
        Optional<User> userOpt = users.findByEmailIgnoreCase(normalized);
        if (userOpt.isEmpty()) {
            log.info("Email verification resend requested for unrecognized email lookup");
            return;
        }

        User user = userOpt.get();
        if (user.isEmailVerified() || !user.isActive()) {
            return;
        }

        sendVerificationEmail(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DomainException("Verification token is required");
        }

        TokenParts tokenParts = parseToken(rawToken.trim());
        EmailVerificationToken token = tokens.findBySelectorForUpdate(tokenParts.selector())
            .orElseThrow(() -> new DomainException("Invalid or expired email verification token"));

        if (token.isUsed() || token.isExpired() || !encoder.matches(tokenParts.verifier(), token.getTokenHash())) {
            throw new DomainException("Invalid or expired email verification token");
        }

        User user = users.findById(token.getUserId())
            .orElseThrow(() -> new DomainException("User account not found"));

        user.setEmailVerified(true);
        user.setUpdatedAt(Instant.now());
        users.save(user);

        token.setUsedAt(Instant.now());
        tokens.save(token);

        log.info("Email successfully verified for user: {}", user.getId());
    }

    private void sendVerificationEmailMessage(String toEmail, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Verification email not dispatched.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(toEmail);
            message.setSubject("EcoLoop Email Verification");
            String verifyLink = verifyBaseUrl + "?token=" + rawToken;
            message.setText("Hello,\n\n"
                + "Please verify your email address for your EcoLoop account:\n\n"
                + verifyLink + "\n\n"
                + "Token: " + rawToken + "\n\n"
                + "This link will expire in " + TOKEN_EXPIRY.toHours() + " hours.\n\n"
                + "The EcoLoop Team");
            mailSender.send(message);
            log.info("Email verification message sent to recipient");
        } catch (Exception e) {
            log.warn("Failed to send email verification message: {}", e.getMessage());
        }
    }

    private String generateVerifier() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private TokenParts parseToken(String rawToken) {
        int separator = rawToken.indexOf('.');
        if (separator <= 0 || separator != rawToken.lastIndexOf('.') || separator == rawToken.length() - 1) {
            throw new DomainException("Invalid or expired email verification token");
        }

        String selector = rawToken.substring(0, separator);
        try {
            if (!UUID.fromString(selector).toString().equals(selector)) {
                throw new DomainException("Invalid or expired email verification token");
            }
        } catch (IllegalArgumentException exception) {
            throw new DomainException("Invalid or expired email verification token");
        }

        return new TokenParts(selector, rawToken.substring(separator + 1));
    }

    private record TokenParts(String selector, String verifier) {}
}
