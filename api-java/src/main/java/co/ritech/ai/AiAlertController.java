package co.ritech.ai;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.security.CurrentUser;
import co.ritech.simulation.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "AI alerts")
@SecurityRequirement(name = "bearer")
public class AiAlertController {

    static final String DISCLAIMER = "Contenido generado por IA; no constituye asesoría financiera";

    private final AiAlertService alerts;
    private final SimulationService simulations;

    public AiAlertController(AiAlertService alerts, SimulationService simulations) {
        this.alerts = alerts;
        this.simulations = simulations;
    }

    public record AiAlertResponse(
            UUID id,
            UUID simulationId,
            AiAlertStatus status,
            String title,
            String content,
            String model,
            Instant createdAt,
            Instant completedAt,
            String disclaimer) {

        static AiAlertResponse from(AiAlert a) {
            return new AiAlertResponse(
                    a.getId(),
                    a.getSimulationId(),
                    a.getStatus(),
                    a.getTitle(),
                    a.getContent(),
                    a.getModel(),
                    a.getCreatedAt(),
                    a.getCompletedAt(),
                    DISCLAIMER);
        }
    }

    @GetMapping("/api/v1/simulations/{id}/alert")
    @Operation(summary = "AI alert of a simulation (owner or ADMIN). 202 while it is still being generated")
    public ResponseEntity<AiAlertResponse> forSimulation(
            @Parameter(hidden = true) CurrentUser user, @PathVariable UUID id) {
        simulations.get(id, user); // ownership check: 404 for simulations of other users
        AiAlert alert = alerts.forSimulation(id);
        HttpStatus status = alert.getStatus() == AiAlertStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(AiAlertResponse.from(alert));
    }

    @GetMapping("/api/v1/me/alerts")
    @Operation(summary = "Own AI alerts, newest first")
    public Page<AiAlertResponse> mine(
            @Parameter(hidden = true) CurrentUser user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return alerts.forUser(user.id(), pageable).map(AiAlertResponse::from);
    }
}
