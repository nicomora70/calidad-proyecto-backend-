package co.ritech.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.ritech.ai.AiAlertReady;
import co.ritech.audit.SecurityEventRecorded;
import co.ritech.market.MarketVariationFetched;
import co.ritech.notification.NotificationEnums.NotificationType;
import co.ritech.user.Role;
import co.ritech.user.User;
import co.ritech.user.UserRepository;

/**
 * Turns domain events into notifications. All triggers are best-effort: a failure here is
 * logged and never affects the operation that raised the event.
 */
@Component
public class NotificationTriggers {

    private static final Logger log = LoggerFactory.getLogger(NotificationTriggers.class);
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");

    private final NotificationService notifications;
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;

    public NotificationTriggers(
            NotificationService notifications, NotificationPreferenceRepository preferences, UserRepository users) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.users = users;
    }

    /** Raised from the AI background thread once the alert is DONE. */
    @EventListener
    public void onAiAlertReady(AiAlertReady event) {
        safely(
                "AI alert",
                () -> notifications.notify(
                        event.userId(),
                        NotificationType.AI_ALERT_READY,
                        "Tu alerta de IA está lista",
                        event.title(),
                        "AI_ALERT:" + event.simulationId()));
    }

    /** Every active user whose threshold is reached by |variation|; once per symbol and market date. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketVariation(MarketVariationFetched event) {
        safely("market variation", () -> {
            BigDecimal move = event.changePct().abs();
            List<User> active = activeUsers(null);
            Map<UUID, NotificationPreference> prefs =
                    preferences.findAllById(active.stream().map(User::getId).toList()).stream()
                            .collect(Collectors.toMap(NotificationPreference::getUserId, Function.identity()));
            String direction = event.changePct().signum() >= 0 ? "subió" : "bajó";
            for (User user : active) {
                var pref = prefs.get(user.getId());
                BigDecimal threshold =
                        pref != null ? pref.getVariationThresholdPct() : notifications.defaultThreshold();
                if (move.compareTo(threshold) >= 0) {
                    notifications.notify(
                            user.getId(),
                            NotificationType.MARKET_VARIATION,
                            "El NASDAQ 100 " + direction + " " + pct(move) + " %",
                            "La variación de " + event.symbol() + " del " + event.marketDate() + " fue de "
                                    + pct(event.changePct()) + " %, igual o mayor a tu umbral de " + pct(threshold)
                                    + " %. Con apalancamiento, el efecto en una simulación es mayor.",
                            "MARKET_VARIATION:" + event.symbol() + ":" + event.marketDate());
                }
            }
        });
    }

    /** Account lockouts and refresh-token reuse go to IT_MANAGER and ADMIN (ISO 27001 A.8.16). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSecurityEvent(SecurityEventRecorded event) {
        safely("security event", () -> {
            String who = users.findById(event.userId()).map(User::getEmail).orElse(String.valueOf(event.userId()));
            String title = switch (event.action()) {
                case "ACCOUNT_LOCKED" -> "Cuenta bloqueada por intentos fallidos";
                case "REFRESH_TOKEN_REUSED" -> "Posible robo de sesión detectado";
                default -> "Evento de seguridad: " + event.action();
            };
            String body =
                    "Usuario afectado: " + who + ". Revisa el registro de auditoría (acción " + event.action() + ").";
            activeUsers(List.of(Role.IT_MANAGER, Role.ADMIN))
                    .forEach(u -> notifications.notify(u.getId(), NotificationType.SECURITY_EVENT, title, body, null));
        });
    }

    private List<User> activeUsers(List<Role> roles) {
        Specification<User> spec = Specification.allOf(UserRepository.notDeleted(), UserRepository.isActive(true));
        if (roles != null) {
            spec = spec.and((root, query, cb) -> root.get("role").in(roles));
        }
        return users.findAll(spec);
    }

    private static String pct(BigDecimal value) {
        NumberFormat f = NumberFormat.getNumberInstance(ES_CO);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(value);
    }

    private static void safely(String trigger, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Notification trigger '{}' failed: {}", trigger, ex.getMessage());
        }
    }
}
