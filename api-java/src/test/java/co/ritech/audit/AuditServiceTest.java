package co.ritech.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import co.ritech.common.DateRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private AuditLogRepository repository;
    private ApplicationEventPublisher events;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new AuditService(repository, events, Clock.fixed(now, ZoneOffset.UTC));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void securityActionsPublishEventForNotifications() {
        UUID user = UUID.randomUUID();

        service.record(user, "ACCOUNT_LOCKED", "USER", user, null);

        verify(events).publishEvent(new SecurityEventRecorded("ACCOUNT_LOCKED", user));
    }

    @Test
    void ordinaryActionsPublishNothing() {
        UUID user = UUID.randomUUID();

        service.record(user, "LOGIN_FAILED", "USER", user, null);

        verifyNoInteractions(events);
    }

    @Test
    void capturesIpAndTruncatedUserAgentFromCurrentRequest() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "x".repeat(500));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        UUID actor = UUID.randomUUID();

        service.record(actor, "LOGIN_SUCCESS", "USER", actor, null);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog entry = captor.getValue();
        assertThat(entry.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(entry.getUserAgent()).hasSize(300);
        assertThat(entry.getCreatedAt()).isEqualTo(now);
        assertThat(entry.getAction()).isEqualTo("LOGIN_SUCCESS");
    }

    @Test
    void worksOutsideHttpRequests() {
        service.record(null, "BOOTSTRAP_ADMIN_CREATED", "USER", null, null);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchDelegatesWithFilters() {
        Pageable page = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        var range = DateRange.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), ZoneId.of("America/Bogota"));

        assertThat(service.search("LOGIN_FAILED", UUID.randomUUID(), range, page))
                .isEmpty();
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }
}
