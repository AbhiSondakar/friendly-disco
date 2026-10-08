package com.ecoloop.common.security;

import com.ecoloop.identity.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActorContextResolverTest {

    @Test
    void throwsOnNullAuthentication() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
            ActorContextResolver.resolve(null));
    }

    @Test
    void throwsOnUnauthenticatedToken() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
            ActorContextResolver.resolve(auth));
    }

    @Test
    void throwsOnAnonymousAuthenticationToken() {
        Authentication auth = new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
            ActorContextResolver.resolve(auth));
    }

    @Test
    void resolvesHouseholdPrincipal() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "test@ecoloop.com", "hash", "HOUSEHOLD", true);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        ActorContext actor = ActorContextResolver.resolve(auth);
        assertNotNull(actor);
        assertEquals(userId, actor.userId());
        assertEquals(Role.HOUSEHOLD, actor.role());
        assertTrue(actor.isHousehold());
        assertFalse(actor.isPartner());
        assertFalse(actor.isAdmin());
    }

    @Test
    void resolvesPartnerPrincipal() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "partner@ecoloop.com", "hash", "PARTNER", true);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        ActorContext actor = ActorContextResolver.resolve(auth);
        assertNotNull(actor);
        assertEquals(userId, actor.userId());
        assertEquals(Role.PARTNER, actor.role());
        assertTrue(actor.isPartner());
        assertFalse(actor.isHousehold());
        assertFalse(actor.isAdmin());
    }

    @Test
    void resolvesAdminPrincipal() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "admin@ecoloop.com", "hash", "ADMIN", true);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        ActorContext actor = ActorContextResolver.resolve(auth);
        assertNotNull(actor);
        assertEquals(userId, actor.userId());
        assertEquals(Role.ADMIN, actor.role());
        assertTrue(actor.isAdmin());
    }

    @Test
    void throwsOnInvalidRole() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "unknown@ecoloop.com", "hash", "INVALID_ROLE", true);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
            ActorContextResolver.resolve(auth));
    }
}
