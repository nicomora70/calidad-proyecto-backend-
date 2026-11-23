package co.ritech.simulation;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.simulation.SimulationDtos.CreateSimulationRequest;
import co.ritech.simulation.SimulationDtos.SimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/simulations")
@Tag(name = "Simulations")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run a simulation: return = 2 x NASDAQ 100 daily variation")
    public SimulationResponse create(@Valid @RequestBody CreateSimulationRequest request) {
        return SimulationResponse.from(service.simulate(request.userId(), request.initialCapital()));
    }

    @GetMapping
    @Operation(summary = "Paged simulation history (optionally filtered by user)")
    public Page<SimulationResponse> list(
            @RequestParam(required = false) UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(userId, pageable).map(SimulationResponse::from);
    }
}
