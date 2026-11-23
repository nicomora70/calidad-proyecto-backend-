package co.ritech.user;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.common.DateRange;
import co.ritech.config.RitechProperties;
import co.ritech.security.CurrentUser;
import co.ritech.simulation.SimulationDtos.SimulationResponse;
import co.ritech.simulation.SimulationService;
import co.ritech.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints about the authenticated user. */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Me")
@SecurityRequirement(name = "bearer")
public class MeController {

    private final UserService users;
    private final SimulationService simulations;
    private final RitechProperties properties;

    public MeController(UserService users, SimulationService simulations, RitechProperties properties) {
        this.users = users;
        this.simulations = simulations;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Profile of the authenticated user")
    public UserResponse me(@Parameter(hidden = true) CurrentUser user) {
        return UserResponse.from(users.get(user.id()));
    }

    @GetMapping("/simulations")
    @Operation(summary = "Own simulation history, filterable by date range (inclusive dates)")
    public Page<SimulationResponse> mySimulations(
            @Parameter(hidden = true) CurrentUser user,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var range = DateRange.of(from, to, properties.timezone());
        return simulations.search(user.id(), range, pageable).map(SimulationResponse::from);
    }
}
