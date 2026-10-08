package com.ecoloop.audit;

import java.util.Locale;
import java.util.Set;

public final class AuditRedactionKeys {

    private AuditRedactionKeys() {}

    public static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
        "password", "token", "secret", "authorization", "cookie", "email", "phone",
        "address", "license", "evidence", "image", "file", "credit_card"
    );

    public static boolean isSensitive(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_NAMES.stream().anyMatch(lower::contains);
    }
}
