package co.ritech.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import co.ritech.notification.NotificationEnums.DeliveryStatus;
import co.ritech.notification.NotificationEnums.NotificationChannel;
import co.ritech.notification.NotificationEnums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One notification for one user on one channel. IN_APP rows are delivered when stored;
 * EMAIL rows are an outbox processed by {@link NotificationDispatcher}.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    private static final int MAX_TITLE = 200;
    private static final int MAX_ERROR = 300;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private NotificationChannel channel;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR)
    private String lastError;

    @Column(name = "dedupe_key", length = 120, updatable = false)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(
            UUID userId,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String body,
            String dedupeKey,
            Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.channel = channel;
        this.title = truncate(title, MAX_TITLE);
        this.body = body;
        this.dedupeKey = dedupeKey;
        this.createdAt = now;
        if (channel == NotificationChannel.IN_APP) {
            this.status = DeliveryStatus.SENT;
            this.sentAt = now;
        } else {
            this.status = DeliveryStatus.PENDING;
            this.nextAttemptAt = now;
        }
    }

    void markSent(Instant now) {
        this.attempts++;
        this.status = DeliveryStatus.SENT;
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /** Exponential backoff (base, 2x, 4x...); FAILED once maxAttempts is reached. */
    void markAttemptFailed(String error, int maxAttempts, Duration baseBackoff, Instant now) {
        this.attempts++;
        this.lastError = truncate(error, MAX_ERROR);
        if (attempts >= maxAttempts) {
            this.status = DeliveryStatus.FAILED;
            this.nextAttemptAt = null;
        } else {
            this.nextAttemptAt = now.plus(baseBackoff.multipliedBy(1L << (attempts - 1)));
        }
    }

    void markFailed(String error) {
        this.status = DeliveryStatus.FAILED;
        this.lastError = truncate(error, MAX_ERROR);
        this.nextAttemptAt = null;
    }

    /** Idempotent: the first read time is kept. */
    void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
