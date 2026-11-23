package co.ritech.license;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.ritech.audit.AuditService;
import co.ritech.config.RitechProperties;
import co.ritech.license.LicenseExceptions.InvalidLicenseException;
import co.ritech.license.LicenseExceptions.LicenseUserLimitException;
import co.ritech.license.LicenseModel.LicenseData;
import co.ritech.license.LicenseModel.LicenseFile;
import co.ritech.license.LicenseModel.Module;
import co.ritech.license.LicenseModel.State;
import co.ritech.user.User;
import co.ritech.user.UserRepository;

/**
 * License in force for this installation.
 * - LICENSE_MODE=disabled (development, demo, CI): everything is enabled.
 * - LICENSE_MODE=enforced: modules come from the plan; when the license is missing or
 *   expired the API is read-only (writes answer 402 LICENSE_EXPIRED, see LicenseInterceptor).
 * On startup the newest stored license is re-verified; if there is none, LICENSE_FILE is
 * installed (first boot of a client installation).
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private final LicenseVerifier verifier;
    private final InstalledLicenseRepository repository;
    private final UserRepository users;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final RitechProperties properties;
    private final Clock clock;

    private volatile LicenseData current;

    public record LicenseStatus(
            State state,
            String licenseId,
            String company,
            LicenseModel.Plan plan,
            Set<Module> modules,
            Integer maxUsers,
            long activeUsers,
            LocalDate issuedAt,
            LocalDate expiresAt,
            Long daysRemaining) {}

    @Autowired
    public LicenseService(
            ResourceLoader resources,
            InstalledLicenseRepository repository,
            UserRepository users,
            AuditService audit,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            ObjectMapper mapper,
            RitechProperties properties,
            Clock clock) {
        this(
                new LicenseVerifier(
                        LicenseVerifier.readPublicKey(
                                resources.getResource(properties.license().publicKeyLocation())),
                        mapper),
                repository,
                users,
                audit,
                events,
                new TransactionTemplate(transactionManager),
                mapper,
                properties,
                clock);
    }

    LicenseService(
            LicenseVerifier verifier,
            InstalledLicenseRepository repository,
            UserRepository users,
            AuditService audit,
            ApplicationEventPublisher events,
            TransactionTemplate transactions,
            ObjectMapper mapper,
            RitechProperties properties,
            Clock clock) {
        this.verifier = verifier;
        this.repository = repository;
        this.users = users;
        this.audit = audit;
        this.events = events;
        this.transactions = transactions;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** Runs before the other startup listeners (news ingestion checks its module). */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initialize() {
        repository.findFirstByOrderByInstalledAtDesc().ifPresent(stored -> {
            try {
                current = verifier.verify(stored.toFile());
            } catch (InvalidLicenseException ex) {
                log.error("Stored license {} failed verification: {}", stored.getLicenseId(), ex.getMessage());
            }
        });
        if (current == null) {
            installFromFile();
        }
        log.info("License state: {}", state());
        checkExpiry();
    }

    private void installFromFile() {
        Path file = Path.of(properties.license().file());
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            LicenseFile license = mapper.readValue(file.toFile(), LicenseFile.class);
            transactions.executeWithoutResult(tx -> install(null, license));
            log.info("License installed from {}", file);
        } catch (IOException | InvalidLicenseException ex) {
            log.error("License file {} could not be installed: {}", file, ex.getMessage());
        }
    }

    /** Verifies and stores a new license (renewal). An already expired license is rejected. */
    @Transactional
    public LicenseStatus install(UUID actorId, LicenseFile file) {
        LicenseData data = verifier.verify(file);
        if (today().isAfter(data.expiresAt())) {
            throw new InvalidLicenseException("License expired on " + data.expiresAt());
        }
        InstalledLicense stored = repository.save(new InstalledLicense(data, file, actorId, clock.instant()));
        audit.record(
                actorId,
                "LICENSE_INSTALLED",
                "LICENSE",
                stored.getId(),
                "license=" + data.licenseId() + " plan=" + data.plan() + " expires=" + data.expiresAt());
        current = data;
        return status();
    }

    public boolean enforced() {
        return "enforced".equalsIgnoreCase(properties.license().mode());
    }

    public State state() {
        if (!enforced()) {
            return State.DISABLED;
        }
        LicenseData license = current;
        if (license == null) {
            return State.MISSING;
        }
        long days = ChronoUnit.DAYS.between(today(), license.expiresAt());
        if (days < 0) {
            return State.EXPIRED;
        }
        return days <= properties.license().expiryWarningDays() ? State.EXPIRING : State.ACTIVE;
    }

    /** Writes are allowed unless the license is missing or expired. */
    public boolean writable() {
        State state = state();
        return state == State.DISABLED || state == State.ACTIVE || state == State.EXPIRING;
    }

    /** An expired license keeps its modules for reading; a missing one enables none. */
    public boolean hasModule(Module module) {
        State state = state();
        if (state == State.DISABLED) {
            return true;
        }
        LicenseData license = current;
        return license != null && license.plan().modules().contains(module);
    }

    /** Called before creating a user. */
    public void checkUserLimit() {
        LicenseData license = current;
        if (enforced() && license != null && activeUsers() >= license.maxUsers()) {
            throw new LicenseUserLimitException(license.maxUsers());
        }
    }

    @Transactional(readOnly = true)
    public LicenseStatus status() {
        LicenseData license = current;
        State state = state();
        if (license == null || state == State.DISABLED) {
            Set<Module> modules = state == State.DISABLED ? Set.of(Module.values()) : Set.of();
            return new LicenseStatus(state, null, null, null, modules, null, activeUsers(), null, null, null);
        }
        return new LicenseStatus(
                state,
                license.licenseId(),
                license.company(),
                license.plan(),
                license.plan().modules(),
                license.maxUsers(),
                activeUsers(),
                license.issuedAt(),
                license.expiresAt(),
                ChronoUnit.DAYS.between(today(), license.expiresAt()));
    }

    /** Company name for branding; null when no license is in force. */
    public String company() {
        LicenseData license = current;
        return license == null ? null : license.company();
    }

    /** Daily reminder while the license is within the warning window (deduplicated by notifications). */
    @Scheduled(cron = "0 0 8 * * *", zone = "${ritech.timezone}")
    public void checkExpiry() {
        LicenseData license = current;
        if (license != null && state() == State.EXPIRING) {
            events.publishEvent(new LicenseExpiringSoon(
                    license.licenseId(),
                    license.company(),
                    license.expiresAt(),
                    ChronoUnit.DAYS.between(today(), license.expiresAt())));
        }
    }

    private long activeUsers() {
        Specification<User> spec = Specification.allOf(UserRepository.notDeleted(), UserRepository.isActive(true));
        return users.count(spec);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(properties.timezone()));
    }
}
