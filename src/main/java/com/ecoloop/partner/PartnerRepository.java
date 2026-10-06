package com.ecoloop.partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PartnerRepository extends JpaRepository<Partner,UUID>, JpaSpecificationExecutor<Partner>{ List<Partner> findAllByStatus(String status); Optional<Partner> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Partner p WHERE p.id = :id")
    Optional<Partner> findByIdForUpdate(@Param("id") UUID id);
}
