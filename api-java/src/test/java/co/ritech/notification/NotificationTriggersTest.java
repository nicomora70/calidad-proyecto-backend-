package co.ritech.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import co.ritech.ai.AiAlertReady;
import co.ritech.audit.SecurityEventRecorded;
import co.ritech.license.LicenseExpiringSoon;
import co.ritech.market.MarketVariationFetched;
import co.ritech.notification.NotificationEnums.NotificationType;
import co.ritech.user.Role;
import co.ritech.user.User;
import co.ritech.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTriggersTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private NotificationService notifications;
    private NotificationPreferenceRepository preferences;
    private UserRepository users;
    private NotificationTriggers triggers;
    private User sensitive;
    private User relaxed;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        notifications = mock(NotificationService.class);
        preferences = mock(NotificationPreferenceRepository.class);
        users = mock(UserRepository.class);
        when(notifications.defaultThreshold()).thenReturn(new BigDecimal("2.00"));
        sensitive = new User("Ana", "ana@c.co", Role.INVESTOR, "h", false, now);
        relaxed = new User("Luis", "luis@c.co", Role.INVESTOR, "h", false, now);
        when(users.findAll(any(Specification.class))).thenReturn(List.of(sensitive, relaxed));
        // Ana: 1.00 %; Luis: no row -> default 2.00 %
        when(preferences.findAllById(any()))
                .thenReturn(List.of(new NotificationPreference(sensitive.getId(), true, true, BigDecimal.ONE, now)));
        triggers = new NotificationTriggers(notifications, preferences, users);
    }

    private void variation(String pct) {
        triggers.onMarketVariation(new MarketVariationFetched("QQQ", LocalDate.of(2026, 11, 20), new BigDecimal(pct)));
    }

    @Test
    void variationNotifiesOnlyUsersWhoseThresholdIsReached() {
        variation("-1.50");

        verify(notifications)
                .notify(
                        eq(sensitive.getId()),
                        eq(NotificationType.MARKET_VARIATION),
                        eq("El NASDAQ 100 bajó 1,50 %"),
                        anyString(),
                        eq("MARKET_VARIATION:QQQ:2026-11-20"));
        verify(notifications, never()).notify(eq(relaxed.getId()), any(), any(), any(), any());
    }

    @Test
    void variationEqualToThresholdNotifies() {
        variation("2.00");

        verify(notifications).notify(eq(relaxed.getId()), eq(NotificationType.MARKET_VARIATION), any(), any(), any());
    }

    @Test
    void variationJustBelowThresholdDoesNotNotify() {
        variation("0.99");

        verify(notifications, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void aiAlertReadyNotifiesOwnerOncePerSimulation() {
        UUID owner = UUID.randomUUID();
        UUID simulation = UUID.randomUUID();

        triggers.onAiAlertReady(new AiAlertReady(owner, simulation, "Subió el NASDAQ"));

        verify(notifications)
                .notify(
                        owner,
                        NotificationType.AI_ALERT_READY,
                        "Tu alerta de IA está lista",
                        "Subió el NASDAQ",
                        "AI_ALERT:" + simulation);
    }

    @Test
    @SuppressWarnings("unchecked")
    void securityEventNotifiesItManagersAndAdmins() {
        User it = new User("IT", "it@c.co", Role.IT_MANAGER, "h", false, now);
        when(users.findAll(any(Specification.class))).thenReturn(List.of(it));
        when(users.findById(sensitive.getId())).thenReturn(Optional.of(sensitive));

        triggers.onSecurityEvent(new SecurityEventRecorded("ACCOUNT_LOCKED", sensitive.getId()));

        verify(notifications)
                .notify(
                        eq(it.getId()),
                        eq(NotificationType.SECURITY_EVENT),
                        eq("Cuenta bloqueada por intentos fallidos"),
                        contains("ana@c.co"),
                        isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void licenseExpiringUsesOneNoticePerWindow() {
        User it = new User("IT", "it@c.co", Role.IT_MANAGER, "h", false, now);
        when(users.findAll(any(Specification.class))).thenReturn(List.of(it));
        LocalDate expires = LocalDate.of(2026, 12, 23);

        triggers.onLicenseExpiring(new LicenseExpiringSoon("RT-1", "Cliente", expires, 30));
        triggers.onLicenseExpiring(new LicenseExpiringSoon("RT-1", "Cliente", expires, 7));

        verify(notifications)
                .notify(
                        eq(it.getId()),
                        eq(NotificationType.LICENSE_EXPIRING),
                        eq("La licencia vence en 30 días"),
                        anyString(),
                        eq("LICENSE_EXPIRING:RT-1:2026-12-23:30"));
        verify(notifications)
                .notify(
                        eq(it.getId()),
                        eq(NotificationType.LICENSE_EXPIRING),
                        eq("La licencia vence en 7 días"),
                        anyString(),
                        eq("LICENSE_EXPIRING:RT-1:2026-12-23:7"));
    }

    @Test
    void triggerFailureIsSwallowed() {
        doThrow(new IllegalStateException("db down")).when(notifications).notify(any(), any(), any(), any(), any());

        assertThatCode(() -> triggers.onAiAlertReady(new AiAlertReady(UUID.randomUUID(), UUID.randomUUID(), "T")))
                .doesNotThrowAnyException();
    }
}
