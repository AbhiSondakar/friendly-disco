package com.ecoloop.pickup;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class PickupSpecifications {

    public static Specification<PickupRequest> withStatus(String status) {
        return (Root<PickupRequest> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status.trim())) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase());
        };
    }

    public static Specification<PickupRequest> withSearch(String query) {
        return (Root<PickupRequest> root, CriteriaQuery<?> q, CriteriaBuilder cb) -> {
            if (!StringUtils.hasText(query)) {
                return cb.conjunction();
            }
            String likePattern = "%" + query.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("address")), likePattern);
        };
    }
}
