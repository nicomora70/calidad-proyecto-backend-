package co.ritech.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.audit.AuditService;
import co.ritech.auth.RefreshTokenRepository;
import co.ritech.license.LicenseService;
import co.ritech.security.CurrentUser;
import co.ritech.user.UserDtos.CreateUserRequest;
import co.ritech.user.UserDtos.UpdateUserRequest;
import co.ritech.user.UserExceptions.EmailAlreadyExistsException;
import co.ritech.user.UserExceptions.LastAdminException;
import co.ritech.user.UserExceptions.UserNotFoundException;

/** User administration (ADMIN only; authorization is enforced in the controller). */
@Service
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final LicenseService license;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            AuditService audit,
            LicenseService license,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.license = license;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public User create(CurrentUser actor, CreateUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmailAndDeletedAtIsNull(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        license.checkUserLimit();
        User user = new User(
                request.fullName(),
                email,
                request.role(),
                passwordEncoder.encode(request.temporaryPassword()),
                true,
                clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException(email); // concurrent creation with the same email
        }
        audit.record(actor.id(), "USER_CREATED", "USER", user.getId(), "role=" + user.getRole());
        return user;
    }

    @Transactional(readOnly = true)
    public Page<User> search(Role role, Boolean active, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserRepository.notDeleted(), UserRepository.hasRole(role), UserRepository.isActive(active));
        return users.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public User update(CurrentUser actor, UUID id, UpdateUserRequest request) {
        User user = get(id);
        Instant now = clock.instant();
        if (request.fullName() != null) {
            user.rename(request.fullName(), now);
        }
        if (request.active() != null && request.active() != user.isActive()) {
            if (!request.active()) {
                ensureNotLastActiveAdmin(user);
                refreshTokens.revokeAllForUser(user.getId(), now);
            }
            user.setActive(request.active(), now);
            audit.record(
                    actor.id(), request.active() ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", user.getId(), null);
        }
        return user;
    }

    @Transactional
    public User changeRole(CurrentUser actor, UUID id, Role newRole) {
        User user = get(id);
        if (user.getRole() == newRole) {
            return user;
        }
        if (newRole != Role.ADMIN) {
            ensureNotLastActiveAdmin(user);
        }
        Role previous = user.getRole();
        user.changeRole(newRole, clock.instant());
        // Force a new login so the new role is reflected in the next access token.
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        audit.record(actor.id(), "ROLE_CHANGED", "USER", user.getId(), previous + "->" + newRole);
        return user;
    }

    /** Soft delete: the user disappears but their simulations stay as financial history. */
    @Transactional
    public void delete(CurrentUser actor, UUID id) {
        User user = get(id);
        ensureNotLastActiveAdmin(user);
        Instant now = clock.instant();
        user.softDelete(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.record(actor.id(), "USER_DELETED", "USER", user.getId(), null);
    }

    /**
     * Definitive suppression of personal data (Ley 1581 de 2012, habeas data). Name, e-mail and
     * password are replaced, sessions revoked, and inbox and preferences deleted (UserAnonymized).
     * Simulations stay without identity (statistics); the audit log is kept as a legal record.
     * Also works on already deleted users. Idempotent.
     */
    @Transactional
    public void anonymize(CurrentUser actor, UUID id) {
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (user.isAnonymized()) {
            return;
        }
        ensureNotLastActiveAdmin(user);
        Instant now = clock.instant();
        String unusablePassword = passwordEncoder.encode(UUID.randomUUID().toString());
        user.anonymize(unusablePassword, now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        events.publishEvent(new UserAnonymized(user.getId()));
        audit.record(actor.id(), "USER_ANONYMIZED", "USER", user.getId(), null);
    }

    private void ensureNotLastActiveAdmin(User user) {
        if (user.getRole() != Role.ADMIN || !user.isActive()) {
            return;
        }
        // Row locks serialize concurrent demotions/deletions of the remaining admins.
        if (users.lockActiveAdmins().size() <= 1) {
            throw new LastAdminException();
        }
    }
}
