package co.ritech.simulation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {

    Page<Simulation> findByUserId(UUID userId, Pageable pageable);

    List<Simulation> findAllByOrderByCreatedAtAsc();
}
