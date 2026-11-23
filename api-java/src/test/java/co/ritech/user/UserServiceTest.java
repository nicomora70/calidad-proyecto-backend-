package co.ritech.user;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.ritech.TestFixtures;
import co.ritech.audit.AuditService;
import co.ritech.auth.RefreshTokenRepository;
import co.ritech.security.CurrentUser;
import co.ritech.user.UserDtos.CreateUserRequest;
import co.ritech.user.UserDtos.UpdateUserRequest;
import co.ritech.user.UserExceptions.EmailAlreadyExistsException;
import co.ritech.user.UserExceptions.LastAdminException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private final CurrentUser actor = new CurrentUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN, false);
    private UserRepository users;
    private RefreshTokenRepository tokens;
    private UserService service;
    private User admin;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(RefreshTokenRepository.class);
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UserService(
                users,
                tokens,
                new BCryptPasswordEncoder(4),
                mock(AuditService.class),
                TestFixtures.allModules(),
                Clock.fixed(now, ZoneOffset.UTC));
        admin = new User("Admin", "admin@example.com", Role.ADMIN, "hash", false, now);
        when(users.findByIdAndDeletedAtIsNull(admin.getId())).thenReturn(Optional.of(admin));
    }

    @Test
    void createdUsersMustChangeTemporaryPassword() {
        User created =
                service.create(actor, new CreateUserRequest("Ana", "Ana@Example.com", Role.INVESTOR, "temporary-123"));

        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.getEmail()).isEqualTo("ana@example.com");
        assertThat(created.getPasswordHash()).isNotEqualTo("temporary-123");
    }

    @Test
    void duplicateEmailIsRejected() {
        when(users.existsByEmailAndDeletedAtIsNull("ana@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                        actor, new CreateUserRequest("Ana", "ana@example.com", Role.INVESTOR, "temporary-123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void cannotDeleteLastAdmin() {
        when(users.lockActiveAdmins()).thenReturn(List.of(admin));
        assertThatThrownBy(() -> service.delete(actor, admin.getId())).isInstanceOf(LastAdminException.class);
    }

    @Test
    void cannotDemoteOrDeactivateLastAdmin() {
        when(users.lockActiveAdmins()).thenReturn(List.of(admin));
        assertThatThrownBy(() -> service.changeRole(actor, admin.getId(), Role.INVESTOR))
                .isInstanceOf(LastAdminException.class);
        assertThatThrownBy(() -> service.update(actor, admin.getId(), new UpdateUserRequest(null, false)))
                .isInstanceOf(LastAdminException.class);
    }

    @Test
    void unknownUserIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(users.findByIdAndDeletedAtIsNull(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(UserExceptions.UserNotFoundException.class);
    }

    @Test
    void concurrentDuplicateEmailIsMappedToConflict() {
        when(users.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("ux_users_email_active"));
        assertThatThrownBy(() -> service.create(
                        actor, new CreateUserRequest("Ana", "ana@example.com", Role.INVESTOR, "temporary-123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void renameAndDeactivateInvestorRevokesSessions() {
        User investor = new User("Ana", "ana@example.com", Role.INVESTOR, "hash", false, now);
        when(users.findByIdAndDeletedAtIsNull(investor.getId())).thenReturn(Optional.of(investor));

        service.update(actor, investor.getId(), new UpdateUserRequest("Ana María", false));

        assertThat(investor.getFullName()).isEqualTo("Ana María");
        assertThat(investor.isActive()).isFalse();
        verify(tokens).revokeAllForUser(eq(investor.getId()), any());

        service.update(actor, investor.getId(), new UpdateUserRequest(null, true));
        assertThat(investor.isActive()).isTrue();
    }

    @Test
    void promotingToAdminDoesNotNeedAnotherAdmin() {
        User investor = new User("Ana", "ana@example.com", Role.INVESTOR, "hash", false, now);
        when(users.findByIdAndDeletedAtIsNull(investor.getId())).thenReturn(Optional.of(investor));

        assertThat(service.changeRole(actor, investor.getId(), Role.ADMIN).getRole())
                .isEqualTo(Role.ADMIN);
        assertThat(service.changeRole(actor, investor.getId(), Role.ADMIN).getRole())
                .isEqualTo(Role.ADMIN); // no-op
        verify(tokens).revokeAllForUser(eq(investor.getId()), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchExcludesDeletedUsers() {
        when(users.findAll(
                        any(org.springframework.data.jpa.domain.Specification.class),
                        any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(admin)));

        var page = service.search(Role.ADMIN, true, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).containsExactly(admin);
    }

    @Test
    void deleteIsSoftAndRevokesSessionsWhenAnotherAdminExists() {
        User other = new User("Other", "other@example.com", Role.ADMIN, "hash", false, now);
        when(users.lockActiveAdmins()).thenReturn(List.of(admin, other));

        service.delete(actor, admin.getId());

        assertThat(admin.getDeletedAt()).isEqualTo(now);
        assertThat(admin.isActive()).isFalse();
        verify(tokens).revokeAllForUser(eq(admin.getId()), any());
    }
}
