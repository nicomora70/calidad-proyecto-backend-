package co.ritech.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.ritech.notification.NotificationEnums.NotificationType;
import co.ritech.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** In-app inbox and notification preferences of the authenticated user. */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearer")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    public record NotificationResponse(
            UUID id, NotificationType type, String title, String body, Instant createdAt, Instant readAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                    n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getCreatedAt(), n.getReadAt());
        }
    }

    public record UnreadCount(long count) {}

    public record ReadAllResult(int updated) {}

    public record PreferencesRequest(
            @NotNull Boolean emailEnabled,
            @NotNull Boolean inAppEnabled,

            @NotNull @DecimalMin("0.10") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
            BigDecimal variationThresholdPct) {}

    /** emailAvailable tells the frontend whether the installation has SMTP configured. */
    public record PreferencesResponse(
            boolean emailEnabled, boolean inAppEnabled, BigDecimal variationThresholdPct, boolean emailAvailable) {}

    @GetMapping("/notifications")
    @Operation(summary = "Own in-app notifications, newest first; unread=true for unread only")
    public Page<NotificationResponse> inbox(
            @Parameter(hidden = true) CurrentUser user,
            @RequestParam(defaultValue = "false") boolean unread,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.inbox(user.id(), unread, pageable).map(NotificationResponse::from);
    }

    @GetMapping("/notifications/unread-count")
    @Operation(summary = "Number of unread in-app notifications (badge)")
    public UnreadCount unreadCount(@Parameter(hidden = true) CurrentUser user) {
        return new UnreadCount(service.unreadCount(user.id()));
    }

    @PatchMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark one own notification as read (idempotent)")
    public void markRead(@Parameter(hidden = true) CurrentUser user, @PathVariable UUID id) {
        service.markRead(user.id(), id);
    }

    @PostMapping("/notifications/read-all")
    @Operation(summary = "Mark all own notifications as read")
    public ReadAllResult markAllRead(@Parameter(hidden = true) CurrentUser user) {
        return new ReadAllResult(service.markAllRead(user.id()));
    }

    @GetMapping("/notification-preferences")
    @Operation(summary = "Own notification preferences (installation defaults until changed)")
    public PreferencesResponse preferences(@Parameter(hidden = true) CurrentUser user) {
        return toResponse(service.preferencesOf(user.id()));
    }

    @PutMapping("/notification-preferences")
    @Operation(summary = "Update channels and the daily variation threshold (%)")
    public PreferencesResponse updatePreferences(
            @Parameter(hidden = true) CurrentUser user, @Valid @RequestBody PreferencesRequest request) {
        return toResponse(service.updatePreferences(
                user.id(), request.emailEnabled(), request.inAppEnabled(), request.variationThresholdPct()));
    }

    private PreferencesResponse toResponse(NotificationPreference p) {
        return new PreferencesResponse(
                p.isEmailEnabled(), p.isInAppEnabled(), p.getVariationThresholdPct(), service.emailAvailable());
    }
}
