package co.ritech.simulation;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.common.DateRange;
import co.ritech.config.RitechProperties;
import co.ritech.security.CurrentUser;
import co.ritech.simulation.SimulationDtos.CreateSimulationRequest;
import co.ritech.simulation.SimulationDtos.SimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/simulations")
@Tag(name = "Simulations")
@SecurityRequirement(name = "bearer")
public class SimulationController {

    private final SimulationService service;
    private final RitechProperties properties;

    public SimulationController(SimulationService service, RitechProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INVESTOR', 'ADMIN')")
    @Operation(summary = "Run a simulation for the authenticated user: return = 2 x NASDAQ 100 daily variation")
    public SimulationResponse create(
            @Parameter(hidden = true) CurrentUser user, @Valid @RequestBody CreateSimulationRequest request) {
        return SimulationResponse.from(service.simulate(user.id(), request.initialCapital()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one simulation (owner or ADMIN)")
    public SimulationResponse get(@Parameter(hidden = true) CurrentUser user, @PathVariable UUID id) {
        return SimulationResponse.from(service.get(id, user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary =
                    "All simulations, filterable by user and date range (ADMIN). Investors use /api/v1/me/simulations")
    public Page<SimulationResponse> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var range = DateRange.of(from, to, properties.timezone());
        return service.search(userId, range, pageable).map(SimulationResponse::from);
    }
}
