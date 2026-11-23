package co.ritech.security;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.ritech.TestFixtures;
import co.ritech.audit.AuditController;
import co.ritech.audit.AuditService;
import co.ritech.user.Role;
import co.ritech.user.UserController;
import co.ritech.user.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Decision table (29119-4 §4.2): audit and user administration per role. */
@WebMvcTest({AuditController.class, UserController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET)
class RoleAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditService audit;

    @MockitoBean
    private UserService users;

    @BeforeEach
    void emptyPages() {
        when(audit.search(any(), any(), any(), any())).thenReturn(Page.empty());
        when(users.search(any(), any(), any())).thenReturn(Page.empty());
    }

    private static JwtRequestPostProcessor as(Role role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("email", "user@example.com")
                        .claim("role", role.name())
                        .claim("pwd_change", false))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @ParameterizedTest(name = "GET /api/v1/audit as {0} -> {1}")
    @CsvSource({"INVESTOR, 403", "IT_MANAGER, 200", "ADMIN, 200"})
    void auditLog(Role role, int expected) throws Exception {
        mvc.perform(get("/api/v1/audit").with(as(role))).andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "GET /api/v1/users as {0} -> {1}")
    @CsvSource({"INVESTOR, 403", "IT_MANAGER, 403", "ADMIN, 200"})
    void listUsers(Role role, int expected) throws Exception {
        mvc.perform(get("/api/v1/users").with(as(role))).andExpect(status().is(expected));
    }

    @ParameterizedTest(name = "POST /api/v1/users as {0} -> 403")
    @CsvSource({"INVESTOR", "IT_MANAGER"})
    void onlyAdminCreatesUsers(Role role) throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(as(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\",\"email\":\"ana@ritech.co\",\"role\":\"INVESTOR\","
                                + "\"temporaryPassword\":\"temporal-123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }
}
