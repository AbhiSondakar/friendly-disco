package com.ecoloop.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Query("SELECT a.id FROM AuditLog a WHERE a.createdAt < :cutoff ORDER BY a.id ASC")
    List<UUID> findExpiredAuditLogIds(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<UUID> ids);
}
