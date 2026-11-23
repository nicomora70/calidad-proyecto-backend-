package co.ritech.settings;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.ritech.TestFixtures;
import co.ritech.security.SecurityConfig;
import co.ritech.settings.BrandingService.Branding;
import co.ritech.user.Role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrandingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET)
class BrandingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BrandingService service;

    private static JwtRequestPostProcessor as(Role role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("email", "u@c.co")
                        .claim("role", role.name())
                        .claim("pwd_change", false))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    private static final Branding SAMPLE = new Branding("RiTech", "Cliente", "#1F6FEB", null, "Aviso");

    @Test
    void getIsPublic() throws Exception {
        when(service.get()).thenReturn(SAMPLE);

        mvc.perform(get("/api/settings/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Cliente"));
    }

    @Test
    void putRequiresAuthenticationAndAdmin() throws Exception {
        String body = "{\"productName\":\"X\"}";
        mvc.perform(put("/api/settings/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/settings/branding")
                        .with(as(Role.INVESTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        verify(service, never()).update(any(), any());
    }

    @Test
    void adminUpdatesWithValidation() throws Exception {
        when(service.update(any(), any())).thenReturn(SAMPLE);

        mvc.perform(put("/api/settings/branding")
                        .with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryColor\":\"#12345G\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/settings/branding")
                        .with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logoUrl\":\"http://insecure.co/logo.png\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/settings/branding")
                        .with(as(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryColor\":\"#12345f\",\"logoUrl\":\"https://cdn.co/logo.png\"}"))
                .andExpect(status().isOk());
    }
}
