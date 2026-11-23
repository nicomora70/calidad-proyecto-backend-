package co.ritech.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAlertRepository extends JpaRepository<AiAlert, UUID> {

    Optional<AiAlert> findBySimulationId(UUID simulationId);

    Page<AiAlert> findByUserId(UUID userId, Pageable pageable);

    @Query("select coalesce(sum(a.costUsd), 0) from AiAlert a"
            + " where a.status = co.ritech.ai.AiAlertStatus.DONE and a.createdAt >= :from")
    BigDecimal spentSince(@Param("from") Instant from);
}
