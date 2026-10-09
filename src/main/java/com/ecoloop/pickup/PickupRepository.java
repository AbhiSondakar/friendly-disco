package com.ecoloop.pickup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface PickupRepository extends JpaRepository<PickupRequest,UUID>, JpaSpecificationExecutor<PickupRequest>{
    List<PickupRequest> findAllByUserIdOrderByCreatedAtDesc(UUID userId); 
    Optional<PickupRequest> findByIdAndUserId(UUID id,UUID userId); 
    List<PickupRequest> findAllByPartnerId(UUID partnerId);
    List<PickupRequest> findAllByPartnerIdOrderByCreatedAtDesc(UUID partnerId);
    long countByStatus(String status);
    List<PickupRequest> findAllByCreatedAtAfter(Instant createdAt);
    
    @Query("SELECT p FROM PickupRequest p WHERE p.deviceId = :deviceId AND p.status NOT IN ('completed', 'cancelled')")
    Optional<PickupRequest> findActiveByDeviceId(@Param("deviceId") UUID deviceId);

    @Query("SELECT p FROM PickupRequest p WHERE p.userId = :userId AND p.deviceId = :deviceId AND p.status NOT IN ('completed', 'cancelled')")
    Optional<PickupRequest> findActiveByUserIdAndDeviceId(@Param("userId") UUID userId, @Param("deviceId") UUID deviceId);

    @Query("SELECT COUNT(p) FROM PickupRequest p WHERE p.partnerId = :partnerId AND p.status IN ('assigned', 'in_transit', 'collected', 'accepted', 'verified') AND p.userId NOT IN (SELECT u.id FROM com.ecoloop.identity.User u WHERE u.deletedAt IS NOT NULL)")
    long countActiveJobsByPartnerId(@Param("partnerId") UUID partnerId);

    @Query("SELECT p.partnerId, COUNT(p) FROM PickupRequest p WHERE p.partnerId IN :partnerIds AND p.status IN ('assigned', 'in_transit', 'collected', 'accepted', 'verified') AND p.userId NOT IN (SELECT u.id FROM com.ecoloop.identity.User u WHERE u.deletedAt IS NOT NULL) GROUP BY p.partnerId")
    List<Object[]> countActiveJobsByPartnerIds(@Param("partnerIds") Collection<UUID> partnerIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PickupRequest p WHERE p.id = :id")
    Optional<PickupRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT DISTINCT p.userId FROM PickupRequest p WHERE p.partnerId = :partnerId AND p.status IN ('assigned', 'in_transit', 'collected', 'accepted', 'verified')")
    List<UUID> findDistinctHouseholdUserIdsByPartnerAssigned(@Param("partnerId") UUID partnerId);

    @Query("SELECT p FROM PickupRequest p WHERE p.partnerId = :partnerId AND p.status IN ('assigned', 'in_transit', 'collected', 'accepted', 'verified')")
    List<PickupRequest> findActiveAssignedToPartner(@Param("partnerId") UUID partnerId);

    /**
     * Admin assignment queue: pending pickups with no partner and no live routing offer.
     * Covers "no eligible partners" escalations, all-offers-expired/rejected pickups, and
     * legacy pickups stranded before routing fixes — anything that needs a manual assignment.
     */
    @Query("SELECT p FROM PickupRequest p WHERE p.status = 'pending' AND p.partnerId IS NULL ORDER BY p.createdAt ASC")
    List<PickupRequest> findUnassigned();
}
