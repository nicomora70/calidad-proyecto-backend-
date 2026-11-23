package co.ritech.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import co.ritech.TestFixtures;
import co.ritech.notification.NotificationEnums.DeliveryStatus;
import co.ritech.notification.NotificationEnums.NotificationChannel;
import co.ritech.notification.NotificationEnums.NotificationType;
import co.ritech.user.Role;
import co.ritech.user.User;
import co.ritech.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private NotificationRepository repository;
    private UserRepository users;
    private EmailSender email;
    private NotificationDispatcher dispatcher;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        users = mock(UserRepository.class);
        email = mock(EmailSender.class);
        when(email.available()).thenReturn(true);
        user = new User("Ana", "ana@cliente.co", Role.INVESTOR, "hash", false, now);
        when(users.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));
        dispatcher = new NotificationDispatcher(
                repository, users, email, TestFixtures.properties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private Notification pendingFor(UUID userId) {
        var n = new Notification(
                userId, NotificationType.AI_ALERT_READY, NotificationChannel.EMAIL, "T", "B", null, now);
        when(repository.findByChannelAndStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                        any(), any(), any(), any()))
                .thenReturn(List.of(n));
        return n;
    }

    @Test
    void sendsDueEmailAndMarksSent() {
        var n = pendingFor(user.getId());

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        verify(email).send("ana@cliente.co", "T", "B");
        assertThat(n.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(n.getAttempts()).isEqualTo(1);
    }

    @Test
    void failureSchedulesRetryWithExponentialBackoffThenFails() {
        var n = pendingFor(user.getId());
        doThrow(new MailSendException("SMTP down")).when(email).send(anyString(), anyString(), anyString());

        dispatcher.dispatchDue(); // attempt 1 -> wait 1 min
        assertThat(n.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(n.getNextAttemptAt()).isEqualTo(now.plus(Duration.ofMinutes(1)));

        dispatcher.dispatchDue(); // attempt 2 -> wait 2 min
        assertThat(n.getNextAttemptAt()).isEqualTo(now.plus(Duration.ofMinutes(2)));

        dispatcher.dispatchDue(); // attempt 3 = max-attempts in TestFixtures
        assertThat(n.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(n.getNextAttemptAt()).isNull();
        assertThat(n.getLastError()).isEqualTo("SMTP down");
    }

    @Test
    void inactiveRecipientIsNotEmailed() {
        user.setActive(false, now);
        var n = pendingFor(user.getId());

        dispatcher.dispatchDue();

        verify(email, never()).send(anyString(), anyString(), anyString());
        assertThat(n.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(n.getLastError()).isEqualTo("USER_INACTIVE");
    }

    @Test
    void nothingHappensWithoutSmtp() {
        when(email.available()).thenReturn(false);

        assertThat(dispatcher.dispatchDue()).isZero();
        verify(repository, never())
                .findByChannelAndStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(any(), any(), any(), any());
    }
}
