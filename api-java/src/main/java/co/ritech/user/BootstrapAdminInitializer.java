package co.ritech.user;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.audit.AuditService;
import co.ritech.config.RitechProperties;

/**
 * Creates the first ADMIN of a fresh installation from BOOTSTRAP_ADMIN_EMAIL /
 * BOOTSTRAP_ADMIN_PASSWORD. Does nothing once any user exists. The password is
 * temporary: it must be changed on first login.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final RitechProperties.Security settings;
    private final Clock clock;

    public BootstrapAdminInitializer(UserRepository users, PasswordEncoder passwordEncoder,
                                     AuditService audit, RitechProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.settings = properties.security();
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.countByDeletedAtIsNull() > 0) {
            return;
        }
        String email = settings.bootstrapAdminEmail();
        String password = settings.bootstrapAdminPassword();
        if (email.isBlank() || password.isBlank()) {
            log.warn("No users exist and BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD are not set: "
                    + "nobody will be able to log in.");
            return;
        }
        if (password.length() < UserDtos.PASSWORD_MIN) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must have at least "
                    + UserDtos.PASSWORD_MIN + " characters");
        }
        User admin = users.save(new User(settings.bootstrapAdminName(), email, Role.ADMIN,
                passwordEncoder.encode(password), true, clock.instant()));
        audit.record(null, "BOOTSTRAP_ADMIN_CREATED", "USER", admin.getId(), null);
        log.info("Bootstrap administrator {} created; password change required on first login", admin.getEmail());
    }
}
