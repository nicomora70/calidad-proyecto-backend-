package co.ritech.settings;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.security.CurrentUser;
import co.ritech.settings.BrandingService.Branding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/settings/branding")
@Tag(name = "Settings")
public class BrandingController {

    private final BrandingService service;

    public BrandingController(BrandingService service) {
        this.service = service;
    }

    public record BrandingRequest(
            @Size(max = 60) String productName,
            @Size(max = 150) String companyName,

            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a #RRGGBB color")
            String primaryColor,

            @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL")
            String logoUrl,

            @Size(max = 1000) String legalNotice) {}

    @GetMapping
    @Operation(summary = "Branding for the frontend (public: needed on the login screen)")
    public Branding get() {
        return service.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Update the branding (ADMIN). Empty fields go back to the default")
    public Branding update(@Parameter(hidden = true) CurrentUser user, @Valid @RequestBody BrandingRequest request) {
        return service.update(
                user.id(),
                new Branding(
                        request.productName(),
                        request.companyName(),
                        request.primaryColor(),
                        request.logoUrl(),
                        request.legalNotice()));
    }
}
