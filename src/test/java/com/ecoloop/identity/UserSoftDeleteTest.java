package com.ecoloop.identity;

import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerController;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupRequest;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.rewards.RewardLedger;
import com.ecoloop.rewards.RewardLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserSoftDeleteTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminUserController adminUserController;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private AuthController authController;

    @Autowired
    private PartnerController partnerController;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private PickupRepository pickupRepository;

    @Autowired
    private RewardLedgerRepository rewardLedgerRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        user = userRepository.save(new User("softdelete@ecoloop.test", "hash", "Soft Delete User", "HOUSEHOLD"));
    }

    @Test
    void userSoftDeleteMarksDeletedAtAndDeactivates() {
        assertNull(user.getDeletedAt());
        assertFalse(user.isDeleted());
        assertTrue(user.isActive());

        user.softDelete();
        user = userRepository.saveAndFlush(user);

        assertNotNull(user.getDeletedAt());
        assertTrue(user.isDeleted());
        assertFalse(user.isActive());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertNotNull(reloaded.getDeletedAt());
        assertTrue(reloaded.isDeleted());
        assertFalse(reloaded.isActive());
    }

    @Test
    void softDeletedUserNotVisibleInAdminListing() {
        User activeUser = userRepository.saveAndFlush(
                new User("active.listing." + UUID.randomUUID() + "@ecoloop.test", "hash", "Active User", "HOUSEHOLD"));

        user.softDelete();
        userRepository.saveAndFlush(user);

        UserPrincipal adminPrincipal = new UserPrincipal(UUID.randomUUID(), "admin@ecoloop.test", "hash", "ADMIN", true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()));

        var listing = adminUserController.list(0, 50, null, null, null);
        assertNotNull(listing);
        assertNotNull(listing.content());

        boolean containsDeleted = listing.content().stream()
                .anyMatch(u -> u.id().equals(user.getId()));
        assertFalse(containsDeleted, "Soft-deleted user must not appear in admin user listing");

        boolean containsActive = listing.content().stream()
                .anyMatch(u -> u.id().equals(activeUser.getId()));
        assertTrue(containsActive, "Active user must appear in admin user listing");
    }

    @Test
    void cannotLogIn() {
        String rawPassword = "ValidPassword123!";
        User authUser = new User("auth.test." + UUID.randomUUID() + "@ecoloop.test",
                passwordEncoder.encode(rawPassword), "Auth User", "HOUSEHOLD");
        authUser = userRepository.saveAndFlush(authUser);

        // Before soft-delete: user can be loaded
        assertNotNull(identityService.loadUserByUsername(authUser.getEmail()));

        // Perform soft delete
        authUser.softDelete();
        userRepository.saveAndFlush(authUser);

        // After soft-delete: user loading fails
        final String email = authUser.getEmail();
        assertThrows(UsernameNotFoundException.class, () -> identityService.loadUserByUsername(email));

        // Attempting login via AuthController fails with 401 Unauthorized
        assertThrows(Exception.class, () -> {
            authController.login(new AuthController.LoginRequest(email, rawPassword),
                    new MockHttpServletRequest(), new MockHttpServletResponse());
        });
    }

    @Test
    void notInPartnerKpis() {
        // 1. Create partner and approved partner profile
        User partnerUser = userRepository.saveAndFlush(
                new User("partner.kpi." + UUID.randomUUID() + "@ecoloop.test", "hash", "Partner KPI", "PARTNER"));
        Partner partner = new Partner(partnerUser.getId(), "KPI Partner Org", "electronics", "LIC-KPI-123");
        partner.setStatus("approved");
        partner.setCapacity(25);
        partner = partnerRepository.saveAndFlush(partner);

        // 2. Create household user and assign active pickup
        User household = userRepository.saveAndFlush(
                new User("hh.kpi." + UUID.randomUUID() + "@ecoloop.test", "hash", "Household KPI", "HOUSEHOLD"));
        PickupRequest pickup = new PickupRequest(household.getId(), null, "789 Green Lane");
        pickup.setPartnerId(partner.getId());
        pickup.setStatus("accepted");
        pickup = pickupRepository.saveAndFlush(pickup);

        // Authenticate as partner
        UserPrincipal principal = UserPrincipal.from(partnerUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        // Active jobs KPI should initially include the active pickup
        Map<String, Object> kpisBefore = partnerController.kpis(new MockHttpServletRequest());
        assertEquals(1L, ((Number) kpisBefore.get("activeJobs")).longValue(),
                "Active pickup from active user must be counted in KPIs");

        // 3. Soft-delete the household user
        household.softDelete();
        userRepository.saveAndFlush(household);

        // Active jobs KPI must exclude jobs from soft-deleted users
        Map<String, Object> kpisAfter = partnerController.kpis(new MockHttpServletRequest());
        assertEquals(0L, ((Number) kpisAfter.get("activeJobs")).longValue(),
                "Pickup from soft-deleted user must not count in active jobs KPI");

        // 4. Soft-delete partner user -> partner cannot retrieve KPIs
        partnerUser.softDelete();
        userRepository.saveAndFlush(partnerUser);
        assertThrows(ResponseStatusException.class, () -> partnerController.kpis(new MockHttpServletRequest()),
                "Soft-deleted partner account must be denied KPI access");
    }

    @Test
    void softDeletedUserPiiIsAnonymized() {
        // GDPR policy: "Never hard-delete users; soft-delete anonymizes email/phone/name/address and leaves ledger intact."
        String originalEmail = "pii.victim." + UUID.randomUUID() + "@ecoloop.test";
        String originalPhone = "+15559876543";
        User piiUser = new User(originalEmail, "hash", "Original Alice Doe", "HOUSEHOLD");
        piiUser.setPhone(originalPhone);
        piiUser.setAddress("742 Evergreen Terrace");
        piiUser = userRepository.saveAndFlush(piiUser);

        // Add reward ledger entry to ensure transactional points history is preserved
        rewardLedgerRepository.saveAndFlush(
                new RewardLedger(piiUser.getId(), 150, "earn", "Initial recycle reward", UUID.randomUUID()));

        // Soft delete user via UserService
        userService.softDeleteUser(piiUser.getId());
        piiUser = userRepository.findById(piiUser.getId()).orElseThrow();

        // Verify status and anonymized PII
        assertTrue(piiUser.isDeleted());
        assertFalse(piiUser.isActive());
        assertNotNull(piiUser.getDeletedAt());
        assertEquals("Anonymized User", piiUser.getName(), "Name must be anonymized");
        assertNull(piiUser.getPhone(), "Phone must be cleared to null");
        assertNull(piiUser.getAddress(), "Address must be cleared to null");
        assertTrue(piiUser.getEmail().startsWith("deleted_"), "Email must be overwritten with anonymized pattern");
        assertTrue(piiUser.getEmail().endsWith("@anonymized.invalid"), "Domain must be RFC 2606 @anonymized.invalid");
        assertEquals("ANONYMIZED_USER_SENTINEL_NON_AUTHENTICATABLE", piiUser.getPasswordHash(),
                "Password hash must be replaced with non-bcrypt sentinel");

        // Verify ledger is intact (GDPR ledger preservation)
        assertEquals(150, rewardLedgerRepository.balance(piiUser.getId()),
                "Reward ledger history must remain intact after soft-delete");

        // Verify former email and phone are freed for re-registration
        User reRegistered = new User(originalEmail, "hash2", "New User Same Email", "HOUSEHOLD");
        reRegistered.setPhone(originalPhone);
        assertDoesNotThrow(() -> userRepository.saveAndFlush(reRegistered),
                "Former email and phone must be immediately available for re-registration without collision");
    }

    @Test
    void softDeleteTransientUserThrowsIllegalStateException() {
        User transientUser = new User("transient@ecoloop.test", "hash", "Transient", "HOUSEHOLD");
        transientUser.setId(null);
        assertThrows(IllegalStateException.class, transientUser::softDelete,
                "Transient entity softDelete must throw IllegalStateException");
    }

    @Test
    void reactivatingSoftDeletedUserFailsWithConflict() {
        user.softDelete();
        userRepository.saveAndFlush(user);

        UserPrincipal adminPrincipal = new UserPrincipal(UUID.randomUUID(), "admin@ecoloop.test", "hash", "ADMIN", true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                adminUserController.activate(user.getId()));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Cannot reactivate a soft-deleted user"));
    }

    @Test
    void softDeleteUserPurgesPasswordResetTokensAndAnonymizesPartner() {
        // 1. Create partner user
        User partnerUser = userRepository.saveAndFlush(
                new User("partner.purge." + UUID.randomUUID() + "@ecoloop.test", "hash", "Partner Purge", "PARTNER"));
        Partner partner = new Partner(partnerUser.getId(), "Original Partner Org", "electronics", "LIC-PURGE-999");
        partner.setServiceAreas("Downtown, Uptown");
        partner.setCapabilities("Batteries, Screens");
        partner.setLicenseUploadId(UUID.randomUUID());
        partner.setStatus("approved");
        partnerRepository.saveAndFlush(partner);

        // 2. Create password reset token
        PasswordResetToken resetToken = new PasswordResetToken(partnerUser.getId(), "selector-123", "validator-hash",
                java.time.Instant.now().plusSeconds(3600));
        passwordResetTokenRepository.saveAndFlush(resetToken);
        assertFalse(passwordResetTokenRepository.findAll().isEmpty());

        // 3. Perform soft delete via UserService
        userService.softDeleteUser(partnerUser.getId());

        // 4. Verify password reset token purged
        assertTrue(passwordResetTokenRepository.findAll().isEmpty(),
                "Password reset tokens for user must be purged on soft delete");

        // 5. Verify partner anonymization
        Partner anonymizedPartner = partnerRepository.findByUserId(partnerUser.getId()).orElseThrow();
        assertEquals("Anonymized Partner", anonymizedPartner.getOrgName());
        assertNull(anonymizedPartner.getServiceAreas());
        assertNull(anonymizedPartner.getCapabilities());
        assertNull(anonymizedPartner.getLicenseNo());
        assertNull(anonymizedPartner.getLicenseUploadId());
        assertEquals("suspended", anonymizedPartner.getStatus());
    }
}
