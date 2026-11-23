package co.ritech.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Per-user preferences. A user without a row uses the installation defaults. */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled;

    @Column(name = "variation_threshold_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal variationThresholdPct;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {}

    public NotificationPreference(
            UUID userId, boolean emailEnabled, boolean inAppEnabled, BigDecimal thresholdPct, Instant now) {
        this.userId = userId;
        update(emailEnabled, inAppEnabled, thresholdPct, now);
    }

    public static NotificationPreference defaults(UUID userId, BigDecimal thresholdPct, Instant now) {
        return new NotificationPreference(userId, true, true, thresholdPct, now);
    }

    void update(boolean emailEnabled, boolean inAppEnabled, BigDecimal thresholdPct, Instant now) {
        this.emailEnabled = emailEnabled;
        this.inAppEnabled = inAppEnabled;
        this.variationThresholdPct = thresholdPct;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public BigDecimal getVariationThresholdPct() {
        return variationThresholdPct;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
