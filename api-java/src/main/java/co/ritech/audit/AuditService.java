package co.ritech.audit;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import co.ritech.common.DateRange;

@Service
public class AuditService {

    private static final int MAX_USER_AGENT = 300;

    /** Actions that alert IT_MANAGER and ADMIN (notifications). */
    static final Set<String> SECURITY_ACTIONS = Set.of("ACCOUNT_LOCKED", "REFRESH_TOKEN_REUSED");

    private final AuditLogRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    /** Must run inside the caller's transaction so the audit entry commits (or not) with it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String entityType, UUID entityId, String details) {
        String ip = null;
        String userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            var request = attrs.getRequest();
            // Behind the reverse proxy, the client IP comes in X-Forwarded-For
            // (enabled via server.forward-headers-strategy=framework).
            ip = request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
                userAgent = userAgent.substring(0, MAX_USER_AGENT);
            }
        }
        repository.save(new AuditLog(actorId, action, entityType, entityId, details, ip, userAgent, clock.instant()));
        if (SECURITY_ACTIONS.contains(action) && entityId != null) {
            events.publishEvent(new SecurityEventRecorded(action, entityId));
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String action, UUID actorId, DateRange range, Pageable pageable) {
        Specification<AuditLog> spec = Specification.allOf(
                AuditLogRepository.hasAction(action),
                AuditLogRepository.hasActor(actorId),
                AuditLogRepository.createdFrom(range.from()),
                AuditLogRepository.createdBefore(range.to()));
        return repository.findAll(spec, pageable);
    }
}
