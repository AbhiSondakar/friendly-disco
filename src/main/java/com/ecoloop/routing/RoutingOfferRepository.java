package com.ecoloop.routing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoutingOfferRepository extends JpaRepository<RoutingOffer, UUID> {
    List<RoutingOffer> findAllByPartnerIdOrderByCreatedAtDesc(UUID partnerId);
    Optional<RoutingOffer> findByIdAndPartnerId(UUID id, UUID partnerId);
    List<RoutingOffer> findAllByPickupId(UUID pickupId);
    boolean existsByPickupIdAndPartnerId(UUID pickupId, UUID partnerId);
    boolean existsByPickupIdAndPartnerIdAndStatusIn(UUID pickupId, UUID partnerId, Collection<String> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT o FROM RoutingOffer o WHERE o.id = :id AND o.partnerId = :partnerId")
    Optional<RoutingOffer> findByIdAndPartnerIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id,
                                                         @org.springframework.data.repository.query.Param("partnerId") UUID partnerId);

    @Query("SELECT o FROM RoutingOffer o WHERE o.status = 'offered' AND o.expiresAt IS NOT NULL AND o.expiresAt < :cutoff")
    Slice<RoutingOffer> findExpiredOffersSliced(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("UPDATE RoutingOffer o SET o.status = 'expired' WHERE o.id IN :ids AND o.status = 'offered'")
    int markAsExpiredInBatch(@Param("ids") Collection<UUID> ids);
}
