package co.ritech.export;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Exports")
@SecurityRequirement(name = "bearer")
public class ExportController {

    static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @GetMapping("/api/me/simulations/export")
    @Operation(summary = "Own simulation history as CSV (inclusive dates)")
    public ResponseEntity<byte[]> mySimulations(
            @Parameter(hidden = true) CurrentUser user,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return file("mis-simulaciones.csv", service.simulations(user.id(), user.id(), from, to));
    }

    @GetMapping("/api/simulations/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All simulations as CSV, optionally for one user (ADMIN)")
    public ResponseEntity<byte[]> allSimulations(
            @Parameter(hidden = true) CurrentUser user,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return file("simulaciones.csv", service.simulations(user.id(), userId, from, to));
    }

    @GetMapping("/api/audit/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'IT_MANAGER')")
    @Operation(summary = "Audit log as CSV (ADMIN, IT_MANAGER)")
    public ResponseEntity<byte[]> audit(
            @Parameter(hidden = true) CurrentUser user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return file("auditoria.csv", service.audit(user.id(), action, actorId, from, to));
    }

    private static ResponseEntity<byte[]> file(String name, byte[] body) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }
}
