package com.ecoloop.identity;

import com.ecoloop.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedAdminUser(UserRepository users,
                                    PasswordEncoder encoder,
                                    Environment env) {
        return args -> {
            boolean seedEnabled = Boolean.parseBoolean(env.getProperty("ecoloop.security.seed-admin.enabled",
                env.getProperty("SEED_ADMIN_ENABLED", "false")));

            if (!seedEnabled) {
                log.info("Admin seeding disabled by configuration");
                return;
            }

            String email = env.getProperty("ADMIN_EMAIL", env.getProperty("ecoloop.security.seed-admin.email", ""));
            String password = env.getProperty("ADMIN_PASSWORD", env.getProperty("ecoloop.security.seed-admin.password", ""));

            if (email.isBlank() || password.isBlank()) {
                log.warn("Admin seeding is enabled, but ADMIN_EMAIL or ADMIN_PASSWORD is blank. Skipping admin seed.");
                return;
            }

            String normalized = User.normalizeEmail(email);
            if (users.findByEmailIgnoreCase(normalized).isPresent()) {
                log.info("Admin user already exists");
                return;
            }

            PasswordPolicy.validate(password);

            User admin = new User();
            admin.setId(UUID.randomUUID());
            admin.setEmail(normalized);
            admin.setPasswordHash(encoder.encode(password));
            admin.setName("System Administrator");
            admin.setRole(Role.ADMIN.name());
            admin.setActive(true);
            admin.setEmailVerified(true);
            admin.setCreatedAt(Instant.now());
            admin.setUpdatedAt(Instant.now());
            users.save(admin);
            log.info("Admin user seeded successfully with configured credentials");
        };
    }
}
