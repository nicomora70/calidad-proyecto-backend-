package co.ritech.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.common.DateRange;
import co.ritech.config.RitechProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit")
@SecurityRequirement(name = "bearer")
public class AuditController {

    private final AuditService service;
    private final RitechProperties properties;

    public AuditController(AuditService service, RitechProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_MANAGER')")
    @Operation(summary = "Search the audit log (ADMIN, IT_MANAGER)")
    public Page<AuditEntryResponse> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var range = DateRange.of(from, to, properties.timezone());
        return service.search(action, actorId, range, pageable).map(AuditEntryResponse::from);
    }

    public record AuditEntryResponse(UUID id, UUID actorId, String action, String entityType,
                                     UUID entityId, String details, String ipAddress,
                                     String userAgent, Instant createdAt) {
        static AuditEntryResponse from(AuditLog a) {
            return new AuditEntryResponse(a.getId(), a.getActorId(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getUserAgent(), a.getCreatedAt());
        }
    }
}
