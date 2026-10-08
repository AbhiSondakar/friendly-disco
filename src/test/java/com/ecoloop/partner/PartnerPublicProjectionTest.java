package com.ecoloop.partner;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserPrincipal;
import com.ecoloop.identity.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PartnerPublicProjectionTest {

    @Autowired
    private PartnerController partnerController;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private UserRepository userRepository;

    private Partner partner;
    private User partnerUser;
    private User householdUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        partnerRepository.deleteAll();
        userRepository.deleteAll();

        partnerUser = new User("partner@ecoloop.test", "hash", "Partner User", "PARTNER");
        partnerUser = userRepository.save(partnerUser);

        householdUser = new User("household@ecoloop.test", "hash", "Household User", "HOUSEHOLD");
        householdUser = userRepository.save(householdUser);

        adminUser = new User("admin@ecoloop.test", "hash", "Admin User", "ADMIN");
        adminUser = userRepository.save(adminUser);

        partner = new Partner(partnerUser.getId(), "Green Earth", "Recycler", "LIC-SECRET-12345");
        partner.setServiceAreas("Seattle, Bellevue");
        partner.setCapabilities("laptop, mobile");
        partner.setCapacity(50);
        partner.setRating(BigDecimal.valueOf(4.8));
        partner.setStatus("approved");
        partner = partnerRepository.save(partner);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User u) {
        UserPrincipal principal = UserPrincipal.from(u);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    void nonOwnerHouseholdCallerReceivesPartnerPublicDtoWithSensitiveFieldsOmitted() {
        authenticateAs(householdUser);
        MockHttpServletRequest request = new MockHttpServletRequest();

        Object response = partnerController.get(partner.getId(), request);
        assertInstanceOf(PartnerPublicDto.class, response, "Must return PartnerPublicDto for non-owner caller");

        PartnerPublicDto publicDto = (PartnerPublicDto) response;
        assertEquals(partner.getId(), publicDto.id());
        assertEquals("Green Earth", publicDto.orgName());
        assertEquals("Recycler", publicDto.type());
        assertEquals("Seattle, Bellevue", publicDto.serviceAreas());
        assertEquals("laptop, mobile", publicDto.capabilities());
        assertEquals(0, BigDecimal.valueOf(4.8).compareTo(publicDto.rating()), "Rating must match 4.8");
    }

    @Test
    void ownerPartnerCallerReceivesFullPartnerDtoWithSensitiveFields() {
        authenticateAs(partnerUser);
        MockHttpServletRequest request = new MockHttpServletRequest();

        Object response = partnerController.get(partner.getId(), request);
        assertInstanceOf(PartnerDto.class, response, "Must return full PartnerDto for owner caller");

        PartnerDto fullDto = (PartnerDto) response;
        assertEquals("LIC-SECRET-12345", fullDto.licenseNo());
        assertEquals(50, fullDto.capacity());
        assertEquals(partnerUser.getId(), fullDto.userId());
    }

    @Test
    void adminCallerReceivesFullPartnerDtoWithSensitiveFields() {
        authenticateAs(adminUser);
        MockHttpServletRequest request = new MockHttpServletRequest();

        Object response = partnerController.get(partner.getId(), request);
        assertInstanceOf(PartnerDto.class, response, "Must return full PartnerDto for admin caller");

        PartnerDto fullDto = (PartnerDto) response;
        assertEquals("LIC-SECRET-12345", fullDto.licenseNo());
        assertEquals(50, fullDto.capacity());
    }
}
