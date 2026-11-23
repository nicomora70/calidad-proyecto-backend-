package co.ritech.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    static Specification<AuditLog> hasAction(String action) {
        return (root, query, cb) -> action == null ? null : cb.equal(root.get("action"), action);
    }

    static Specification<AuditLog> hasActor(UUID actorId) {
        return (root, query, cb) -> actorId == null ? null : cb.equal(root.get("actorId"), actorId);
    }

    static Specification<AuditLog> createdFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<AuditLog> createdBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("createdAt"), to);
    }
}
