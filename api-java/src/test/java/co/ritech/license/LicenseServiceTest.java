package co.ritech.license;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.ritech.TestFixtures;
import co.ritech.audit.AuditService;
import co.ritech.config.RitechProperties;
import co.ritech.license.LicenseExceptions.InvalidLicenseException;
import co.ritech.license.LicenseExceptions.LicenseUserLimitException;
import co.ritech.license.LicenseModel.Module;
import co.ritech.license.LicenseModel.State;
import co.ritech.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LicenseServiceTest {

    /** 2026-11-23 10:00 in Bogota (UTC-5). */
    private static final Instant NOW = Instant.parse("2026-11-23T15:00:00Z");

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private final TestLicenses licenses = new TestLicenses();
    private InstalledLicenseRepository repository;
    private UserRepository users;
    private AuditService audit;
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        repository = mock(InstalledLicenseRepository.class);
        users = mock(UserRepository.class);
        audit = mock(AuditService.class);
        events = mock(ApplicationEventPublisher.class);
        when(repository.save(any(InstalledLicense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByOrderByInstalledAtDesc()).thenReturn(Optional.empty());
    }

    private LicenseService service(String mode, Instant now) {
        RitechProperties base = TestFixtures.properties();
        RitechProperties props = new RitechProperties(
                base.simulation(),
                base.market(),
                base.calcEngine(),
                base.cors(),
                base.security(),
                base.ai(),
                base.news(),
                base.notifications(),
                new RitechProperties.License(mode, "/nonexistent/license.json", "unused", 30),
                base.rateLimit(),
                base.timezone());
        return new LicenseService(
                new LicenseVerifier(licenses.publicKey(), new ObjectMapper()),
                repository,
                users,
                audit,
                events,
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                new ObjectMapper(),
                props,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private LicenseService enforcedWith(String plan, int maxUsers, LocalDate expires) {
        var service = service("enforced", NOW);
        service.install(null, licenses.issue(plan, maxUsers, TODAY.minusDays(1), expires));
        return service;
    }

    @Test
    void disabledModeEnablesEverything() {
        var service = service("disabled", NOW);

        assertThat(service.state()).isEqualTo(State.DISABLED);
        assertThat(service.writable()).isTrue();
        assertThat(service.hasModule(Module.PROJECTIONS)).isTrue();
        assertThatCode(service::checkUserLimit).doesNotThrowAnyException();
        assertThat(service.status().modules()).containsExactlyInAnyOrder(Module.values());
    }

    @Test
    void enforcedWithoutLicenseIsReadOnlyWithNoModules() {
        var service = service("enforced", NOW);

        assertThat(service.state()).isEqualTo(State.MISSING);
        assertThat(service.writable()).isFalse();
        assertThat(service.hasModule(Module.SIMULATIONS)).isFalse();
    }

    @Test
    void planDefinesModules() {
        var basic = enforcedWith("BASIC", 5, TODAY.plusDays(200));
        assertThat(basic.hasModule(Module.SIMULATIONS)).isTrue();
        assertThat(basic.hasModule(Module.AI_ALERTS)).isFalse();

        var professional = enforcedWith("PROFESSIONAL", 5, TODAY.plusDays(200));
        assertThat(professional.hasModule(Module.EXPORTS)).isTrue();
        assertThat(professional.hasModule(Module.PROJECTIONS)).isFalse();

        assertThat(enforcedWith("ENTERPRISE", 5, TODAY.plusDays(200)).hasModule(Module.PROJECTIONS))
                .isTrue();
    }

    @Test
    void warningWindowBoundaries() {
        assertThat(enforcedWith("BASIC", 5, TODAY.plusDays(31)).state()).isEqualTo(State.ACTIVE);
        assertThat(enforcedWith("BASIC", 5, TODAY.plusDays(30)).state()).isEqualTo(State.EXPIRING);
    }

    @Test
    void lastDayIsStillValidAndTheNextIsReadOnly() {
        var service = enforcedWith("BASIC", 5, TODAY);
        assertThat(service.state()).isEqualTo(State.EXPIRING);
        assertThat(service.writable()).isTrue();

        // Same license one day later: re-verified from the stored row
        var stored = new InstalledLicense(
                new LicenseVerifier(licenses.publicKey(), new ObjectMapper())
                        .verify(licenses.issue("BASIC", 5, TODAY.minusDays(1), TODAY)),
                licenses.issue("BASIC", 5, TODAY.minusDays(1), TODAY),
                null,
                NOW);
        when(repository.findFirstByOrderByInstalledAtDesc()).thenReturn(Optional.of(stored));
        var tomorrow = service("enforced", NOW.plus(Duration.ofDays(1)));
        tomorrow.initialize();

        assertThat(tomorrow.state()).isEqualTo(State.EXPIRED);
        assertThat(tomorrow.writable()).isFalse();
        assertThat(tomorrow.hasModule(Module.SIMULATIONS)).isTrue(); // reads keep working
    }

    @Test
    void expiredLicenseCannotBeInstalled() {
        var service = service("enforced", NOW);
        var expired = licenses.issue("BASIC", 5, TODAY.minusDays(10), TODAY.minusDays(1));

        assertThatThrownBy(() -> service.install(null, expired)).isInstanceOf(InvalidLicenseException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void userLimitIsReachedAtMaxUsers() {
        var service = enforcedWith("BASIC", 3, TODAY.plusDays(100));

        when(users.count(any(Specification.class))).thenReturn(2L);
        assertThatCode(service::checkUserLimit).doesNotThrowAnyException();

        when(users.count(any(Specification.class))).thenReturn(3L);
        assertThatThrownBy(service::checkUserLimit).isInstanceOf(LicenseUserLimitException.class);
    }

    @Test
    void installIsAudited() {
        enforcedWith("BASIC", 3, TODAY.plusDays(100));
        verify(audit).record(isNull(), eq("LICENSE_INSTALLED"), eq("LICENSE"), any(), any());
    }

    @Test
    void expiringLicensePublishesReminder() {
        var service = enforcedWith("BASIC", 3, TODAY.plusDays(7));

        service.checkExpiry();

        verify(events).publishEvent(new LicenseExpiringSoon("RT-TEST-1", "Cliente S.A.S.", TODAY.plusDays(7), 7));
    }

    @Test
    void activeLicensePublishesNothing() {
        var service = enforcedWith("BASIC", 3, TODAY.plusDays(100));

        service.checkExpiry();

        verify(events, never()).publishEvent(any(LicenseExpiringSoon.class));
        assertThat(service.status().daysRemaining()).isEqualTo(100);
        assertThat(service.company()).isEqualTo("Cliente S.A.S.");
    }

    @Test
    void tamperedStoredLicenseIsIgnoredOnStartup() {
        var genuine = licenses.issue("BASIC", 5, TODAY.minusDays(1), TODAY.plusDays(100));
        var data = new LicenseVerifier(licenses.publicKey(), new ObjectMapper()).verify(genuine);
        var forgedFile = new TestLicenses().issue("ENTERPRISE", 500, TODAY.minusDays(1), TODAY.plusDays(100));
        when(repository.findFirstByOrderByInstalledAtDesc())
                .thenReturn(Optional.of(new InstalledLicense(data, forgedFile, null, NOW)));
        var service = service("enforced", NOW);

        service.initialize();

        assertThat(service.state()).isEqualTo(State.MISSING);
    }
}
