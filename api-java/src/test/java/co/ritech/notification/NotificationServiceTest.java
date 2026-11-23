package co.ritech.notification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import co.ritech.TestFixtures;
import co.ritech.notification.NotificationEnums.DeliveryStatus;
import co.ritech.notification.NotificationEnums.NotificationChannel;
import co.ritech.notification.NotificationEnums.NotificationType;
import co.ritech.notification.NotificationService.NotificationNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final List<Notification> saved = new ArrayList<>();
    private NotificationRepository repository;
    private NotificationPreferenceRepository preferences;
    private EmailSender email;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        preferences = mock(NotificationPreferenceRepository.class);
        email = mock(EmailSender.class);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(preferences.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        when(preferences.findById(userId)).thenReturn(Optional.empty());
        service = new NotificationService(
                repository,
                preferences,
                email,
                TestFixtures.properties(),
                TestFixtures.allModules(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void notifyOnce(String dedupeKey) {
        service.notify(userId, NotificationType.AI_ALERT_READY, "T", "B", dedupeKey);
    }

    @Test
    void withoutSmtpOnlyInAppIsCreatedAndDelivered() {
        when(email.available()).thenReturn(false);

        notifyOnce(null);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(saved.get(0).getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void withSmtpEmailIsQueuedAsPending() {
        when(email.available()).thenReturn(true);

        notifyOnce(null);

        assertThat(saved)
                .extracting(Notification::getChannel)
                .containsExactly(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        Notification mail = saved.get(1);
        assertThat(mail.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(mail.getNextAttemptAt()).isEqualTo(now);
    }

    @Test
    void disabledChannelsAreSkipped() {
        when(email.available()).thenReturn(true);
        when(preferences.findById(userId))
                .thenReturn(Optional.of(new NotificationPreference(userId, false, false, BigDecimal.ONE, now)));

        notifyOnce(null);

        assertThat(saved).isEmpty();
    }

    @Test
    void duplicateDedupeKeyIsIgnored() {
        when(repository.existsByUserIdAndChannelAndDedupeKey(userId, NotificationChannel.IN_APP, "K"))
                .thenReturn(true);

        notifyOnce("K");

        assertThat(saved).isEmpty();
    }

    @Test
    void defaultsApplyUntilUserSavesPreferences() {
        var pref = service.preferencesOf(userId);
        assertThat(pref.isEmailEnabled()).isTrue();
        assertThat(pref.isInAppEnabled()).isTrue();
        assertThat(pref.getVariationThresholdPct()).isEqualByComparingTo("2.00");

        var updated = service.updatePreferences(userId, false, true, new BigDecimal("1.50"));

        assertThat(updated.isEmailEnabled()).isFalse();
        assertThat(updated.getVariationThresholdPct()).isEqualByComparingTo("1.50");
    }

    @Test
    void markReadIsIdempotentAndOwnerOnly() {
        var n = new Notification(
                userId, NotificationType.AI_ALERT_READY, NotificationChannel.IN_APP, "T", "B", null, now);
        when(repository.findByIdAndUserIdAndChannel(n.getId(), userId, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(n));

        service.markRead(userId, n.getId());
        service.markRead(userId, n.getId());

        assertThat(n.getReadAt()).isEqualTo(now);
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.markRead(other, n.getId())).isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void inboxUnreadCountAndReadAllUseInAppChannel() {
        when(repository.countByUserIdAndChannelAndReadAtIsNull(userId, NotificationChannel.IN_APP))
                .thenReturn(3L);
        when(repository.markAllRead(eq(userId), eq(now))).thenReturn(3);

        assertThat(service.unreadCount(userId)).isEqualTo(3);
        assertThat(service.markAllRead(userId)).isEqualTo(3);
        service.inbox(userId, true, PageRequest.of(0, 20));
        service.inbox(userId, false, PageRequest.of(0, 20));
    }
}
