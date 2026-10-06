package com.ecoloop.identity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of(
        "admin@123",
        "password@123",
        "admin12345678",
        "password12345",
        "password123456",
        "ecoloop123456",
        "administrator",
        "123456789012",
        "qwertyuiopas",
        "letmein123456",
        "welcome123456"
    );

    private static final Map<Character, Character> LEET_SUBSTITUTIONS = Map.of(
        '@', 'a', '$', 's', '0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's', '7', 't'
    );

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters in length");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password must not exceed " + MAX_LENGTH + " characters in length");
        }

        String normalized = normalizeForCommonPasswordCheck(password);
        if (FORBIDDEN_PASSWORDS.stream()
            .map(PasswordPolicy::normalizeForCommonPasswordCheck)
            .anyMatch(normalized::equals)) {
            throw new IllegalArgumentException("Password is too common or matches a known default password");
        }

        boolean hasLetter = false;
        boolean hasDigitOrSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else {
                hasDigitOrSpecial = true;
            }
        }

        if (!hasLetter || !hasDigitOrSpecial) {
            throw new IllegalArgumentException("Password must contain both letters and digits or special characters");
        }
    }

    private static String normalizeForCommonPasswordCheck(String password) {
        String compatibilityNormalized = Normalizer.normalize(password, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(compatibilityNormalized.length());
        for (int index = 0; index < compatibilityNormalized.length(); index++) {
            char character = compatibilityNormalized.charAt(index);
            char canonical = LEET_SUBSTITUTIONS.getOrDefault(character, character);
            if (Character.isLetterOrDigit(canonical)) {
                normalized.append(canonical);
            }
        }
        return normalized.toString();
    }
}
