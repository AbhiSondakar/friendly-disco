package com.ecoloop.identity;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecifications {

    public static Specification<User> withRole(String role) {
        return (Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (role == null || role.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(root.get("role"), role);
        };
    }

    public static Specification<User> withActive(Boolean active) {
        return (Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (active == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("active"), active);
        };
    }

    public static Specification<User> withSearch(String query) {
        return (Root<User> root, CriteriaQuery<?> cq, CriteriaBuilder cb) -> {
            if (query == null || query.isBlank()) {
                return cb.conjunction();
            }
            String likePattern = "%" + query.toLowerCase(java.util.Locale.ROOT) + "%";
            Predicate nameLike = cb.like(cb.lower(root.get("name")), likePattern);
            Predicate emailLike = cb.like(cb.lower(root.get("email")), likePattern);
            return cb.or(nameLike, emailLike);
        };
    }
}
