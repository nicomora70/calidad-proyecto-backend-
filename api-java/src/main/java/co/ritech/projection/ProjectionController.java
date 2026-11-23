package co.ritech.projection;

import java.math.BigDecimal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.projection.ProjectionService.Projection;
import co.ritech.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/projections")
@Tag(name = "Projections")
@SecurityRequirement(name = "bearer")
public class ProjectionController {

    static final String DISCLAIMER =
            "Estimación a partir de variaciones históricas; no es una promesa de rentabilidad ni asesoría financiera";

    private final ProjectionService service;

    public ProjectionController(ProjectionService service) {
        this.service = service;
    }

    /** horizonDays are trading days (252 = about one year). */
    public record ProjectionRequest(
            @NotNull @Positive @Digits(integer = 16, fraction = 2)
            BigDecimal initialCapital,

            @NotNull @Min(1) @Max(252) Integer horizonDays) {}

    public record ProjectionResponse(Projection projection, String disclaimer) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('INVESTOR', 'ADMIN')")
    @Operation(summary = "Pessimistic (P10), base (P50) and optimistic (P90) scenarios by Monte Carlo (Enterprise)")
    public ProjectionResponse project(
            @Parameter(hidden = true) CurrentUser user, @Valid @RequestBody ProjectionRequest request) {
        return new ProjectionResponse(
                service.project(user.id(), request.initialCapital(), request.horizonDays()), DISCLAIMER);
    }
}
