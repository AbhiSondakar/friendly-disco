package com.ecoloop.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public class AuditSpecifications {

    public static Specification<AuditLog> withSearch(String query) {
        return (root, criteriaQuery, cb) -> {
            if (query == null || query.isBlank()) {
                return null;
            }
            String q = "%" + query.toLowerCase() + "%";
            Predicate action = cb.like(cb.lower(root.get("action")), q);
            Predicate actorRole = cb.like(cb.lower(root.get("actorRole")), q);
            Predicate entityType = cb.like(cb.lower(root.get("entityType")), q);
            Predicate result = cb.like(cb.lower(root.get("result")), q);
            return cb.or(action, actorRole, entityType, result);
        };
    }
}
