package co.ritech.license;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.license.LicenseModel.LicenseFile;
import co.ritech.license.LicenseService.LicenseStatus;
import co.ritech.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/license")
@Tag(name = "License")
@SecurityRequirement(name = "bearer")
public class LicenseController {

    private final LicenseService service;

    public LicenseController(LicenseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_MANAGER')")
    @Operation(summary = "License in force: state, plan, modules, users and expiry (ADMIN, IT_MANAGER)")
    public LicenseStatus status() {
        return service.status();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('IT_MANAGER', 'ADMIN')")
    @Operation(summary = "Install or renew the license with the signed file content (IT_MANAGER, ADMIN)")
    public LicenseStatus install(@Parameter(hidden = true) CurrentUser user, @RequestBody LicenseFile file) {
        return service.install(user.id(), file);
    }
}
