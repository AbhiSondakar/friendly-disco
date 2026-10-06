package com.ecoloop;

import com.ecoloop.identity.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void rejectsShortPasswords() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            PasswordPolicy.validate("Short1!"));
        assertTrue(ex.getMessage().contains("at least 12 characters"));
    }

    @Test
    void rejectsCommonDefaultPasswords() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            PasswordPolicy.validate("Admin@123456"));
        assertTrue(ex.getMessage().contains("too common"));
    }

    @Test
    void rejectsLeetspeakVariantsOfForbiddenDefaults() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            PasswordPolicy.validate("pa55w0rd12345"));
        assertTrue(ex.getMessage().contains("too common"));
    }

    @Test
    void doesNotRejectAUniquePasswordOnlyBecauseItContainsAForbiddenDefault() {
        assertDoesNotThrow(() ->
            PasswordPolicy.validate("My-admin@123-unique-2026"));
    }

    @Test
    void rejectsSingleCharacterClassPasswords() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            PasswordPolicy.validate("onlylettersallowedhere"));
        assertTrue(ex.getMessage().contains("contain both letters and digits or special characters"));
    }

    @Test
    void acceptsStrongValidPassword() {
        assertDoesNotThrow(() ->
            PasswordPolicy.validate("SecurePassw0rd!2026"));
    }
}
