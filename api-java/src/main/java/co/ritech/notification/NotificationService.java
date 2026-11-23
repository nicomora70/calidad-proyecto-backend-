package co.ritech.notification;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.common.BusinessException;
import co.ritech.config.RitechProperties;
import co.ritech.license.LicenseModel.Module;
import co.ritech.license.LicenseService;
import co.ritech.notification.NotificationEnums.NotificationChannel;
import co.ritech.notification.NotificationEnums.NotificationType;

/** Creates notifications according to each user's preferences, and serves the in-app inbox. */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationPreferenceRepository preferences;
    private final EmailSender email;
    private final RitechProperties properties;
    private final LicenseService license;
    private final Clock clock;

    public NotificationService(
            NotificationRepository repository,
            NotificationPreferenceRepository preferences,
            EmailSender email,
            RitechProperties properties,
            LicenseService license,
            Clock clock) {
        this.repository = repository;
        this.preferences = preferences;
        this.email = email;
        this.properties = properties;
        this.license = license;
        this.clock = clock;
    }

    public static class NotificationNotFoundException extends BusinessException {
        public NotificationNotFoundException(UUID id) {
            super(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification " + id + " does not exist");
        }
    }

    /**
     * One row per enabled channel. E-mail rows are queued (outbox); in-app rows are delivered at once.
     * A non-null dedupeKey makes the call idempotent per user and channel.
     */
    @Transactional
    public void notify(UUID userId, NotificationType type, String title, String body, String dedupeKey) {
        boolean operational = type == NotificationType.SECURITY_EVENT || type == NotificationType.LICENSE_EXPIRING;
        if (!operational && !license.hasModule(Module.NOTIFICATIONS)) {
            return; // security and license notices are sent on every plan
        }
        NotificationPreference pref = preferencesOf(userId);
        if (pref.isInAppEnabled()) {
            create(userId, type, NotificationChannel.IN_APP, title, body, dedupeKey);
        }
        if (pref.isEmailEnabled() && email.available()) {
            create(userId, type, NotificationChannel.EMAIL, title, body, dedupeKey);
        }
    }

    private void create(
            UUID userId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String body,
            String dedupeKey) {
        if (dedupeKey != null && repository.existsByUserIdAndChannelAndDedupeKey(userId, channel, dedupeKey)) {
            return;
        }
        repository.save(new Notification(userId, type, channel, title, body, dedupeKey, clock.instant()));
    }

    /** Stored preferences, or the installation defaults (not persisted) for users who never changed them. */
    @Transactional(readOnly = true)
    public NotificationPreference preferencesOf(UUID userId) {
        return preferences
                .findById(userId)
                .orElseGet(() -> NotificationPreference.defaults(userId, defaultThreshold(), clock.instant()));
    }

    @Transactional
    public NotificationPreference updatePreferences(
            UUID userId, boolean emailEnabled, boolean inAppEnabled, BigDecimal thresholdPct) {
        NotificationPreference pref = preferences
                .findById(userId)
                .orElseGet(() -> NotificationPreference.defaults(userId, defaultThreshold(), clock.instant()));
        pref.update(emailEnabled, inAppEnabled, thresholdPct, clock.instant());
        return preferences.save(pref);
    }

    public BigDecimal defaultThreshold() {
        return properties.notifications().defaultVariationThresholdPct();
    }

    public boolean emailAvailable() {
        return email.available();
    }

    @Transactional(readOnly = true)
    public Page<Notification> inbox(UUID userId, boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? repository.findByUserIdAndChannelAndReadAtIsNull(userId, NotificationChannel.IN_APP, pageable)
                : repository.findByUserIdAndChannel(userId, NotificationChannel.IN_APP, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndChannelAndReadAtIsNull(userId, NotificationChannel.IN_APP);
    }

    /** Only the owner's in-app notifications; anything else is 404. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = repository
                .findByIdAndUserIdAndChannel(notificationId, userId, NotificationChannel.IN_APP)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        n.markRead(clock.instant());
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, clock.instant());
    }
}
