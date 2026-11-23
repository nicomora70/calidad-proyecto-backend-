package co.ritech.notification;

public final class NotificationEnums {

    private NotificationEnums() {}

    public enum NotificationType {
        AI_ALERT_READY,
        MARKET_VARIATION,
        SECURITY_EVENT,
        /* Triggered by the license module (Phase 5) */
        LICENSE_EXPIRING
    }

    public enum NotificationChannel {
        IN_APP,
        EMAIL
    }

    public enum DeliveryStatus {
        PENDING,
        SENT,
        FAILED
    }
}
