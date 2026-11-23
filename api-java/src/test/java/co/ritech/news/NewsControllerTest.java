package co.ritech.news;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.ritech.TestFixtures;
import co.ritech.news.NewsExceptions.NewsQuotaExceededException;
import co.ritech.news.NewsIngestionService.RefreshResult;
import co.ritech.security.SecurityConfig;
import co.ritech.user.Role;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET)
class NewsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NewsService news;

    @MockitoBean
    private NewsIngestionService ingestion;

    private static JwtRequestPostProcessor as(Role role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("email", "user@example.com")
                        .claim("role", role.name())
                        .claim("pwd_change", false))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Test
    void adminCanRefresh() throws Exception {
        when(ingestion.refresh()).thenReturn(new RefreshResult(3, 2));

        mvc.perform(post("/api/news/refresh").with(as(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2));
    }

    @Test
    void investorCannotRefresh() throws Exception {
        mvc.perform(post("/api/news/refresh").with(as(Role.INVESTOR))).andExpect(status().isForbidden());
        verify(ingestion, never()).refresh();
    }

    @Test
    void quotaExceededIs429() throws Exception {
        when(ingestion.refresh()).thenThrow(new NewsQuotaExceededException(4));

        mvc.perform(post("/api/news/refresh").with(as(Role.ADMIN)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("NEWS_QUOTA_EXCEEDED"));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/news")).andExpect(status().isUnauthorized());
    }
}
