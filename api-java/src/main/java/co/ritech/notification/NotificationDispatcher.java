package co.ritech.notification;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.ritech.config.RitechProperties;
import co.ritech.notification.NotificationEnums.DeliveryStatus;
import co.ritech.notification.NotificationEnums.NotificationChannel;
import co.ritech.user.User;
import co.ritech.user.UserRepository;

/**
 * Outbox worker: delivers due e-mails, retrying with exponential backoff until
 * NOTIFICATION_MAX_ATTEMPTS. One API instance per installation, so a single scheduler
 * thread is enough (no row locking between workers).
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH = 50;

    private final NotificationRepository repository;
    private final UserRepository users;
    private final EmailSender email;
    private final RitechProperties properties;
    private final Clock clock;

    public NotificationDispatcher(
            NotificationRepository repository,
            UserRepository users,
            EmailSender email,
            RitechProperties properties,
            Clock clock) {
        this.repository = repository;
        this.users = users;
        this.email = email;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return number of e-mails processed in this run */
    @Scheduled(
            fixedDelayString = "${ritech.notifications.dispatch-interval-ms:30000}",
            initialDelayString = "${ritech.notifications.dispatch-interval-ms:30000}")
    public int dispatchDue() {
        if (!email.available()) {
            return 0;
        }
        var due = repository.findByChannelAndStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                NotificationChannel.EMAIL, DeliveryStatus.PENDING, clock.instant(), PageRequest.of(0, BATCH));
        due.forEach(this::deliver);
        return due.size();
    }

    private void deliver(Notification n) {
        var recipient = users.findByIdAndDeletedAtIsNull(n.getUserId()).filter(User::isActive);
        if (recipient.isEmpty()) {
            n.markFailed("USER_INACTIVE");
        } else {
            try {
                email.send(recipient.get().getEmail(), n.getTitle(), n.getBody());
                n.markSent(clock.instant());
            } catch (RuntimeException ex) {
                log.warn("E-mail {} attempt {} failed: {}", n.getId(), n.getAttempts() + 1, ex.getMessage());
                var cfg = properties.notifications();
                n.markAttemptFailed(ex.getMessage(), cfg.maxAttempts(), cfg.retryBackoff(), clock.instant());
            }
        }
        repository.save(n);
    }
}
