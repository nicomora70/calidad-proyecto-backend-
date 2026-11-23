package co.ritech.notification;

import java.math.BigDecimal;
import java.time.Instant;
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
import co.ritech.notification.NotificationService.NotificationNotFoundException;
import co.ritech.security.SecurityConfig;
import co.ritech.user.Role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "ritech.security.jwt-secret=" + TestFixtures.JWT_SECRET)
class NotificationControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationService service;

    private static JwtRequestPostProcessor investor() {
        return jwt().jwt(j -> j.subject(USER_ID.toString())
                        .claim("email", "ana@cliente.co")
                        .claim("role", "INVESTOR")
                        .claim("pwd_change", false))
                .authorities(new SimpleGrantedAuthority("ROLE_" + Role.INVESTOR.name()));
    }

    private static String prefs(String threshold) {
        return "{\"emailEnabled\":false,\"inAppEnabled\":true,\"variationThresholdPct\":" + threshold + "}";
    }

    @Test
    void updatesPreferences() throws Exception {
        when(service.updatePreferences(USER_ID, false, true, new BigDecimal("1.50")))
                .thenReturn(new NotificationPreference(USER_ID, false, true, new BigDecimal("1.50"), Instant.now()));

        mvc.perform(put("/api/v1/me/notification-preferences")
                        .with(investor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prefs("1.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variationThresholdPct").value(1.50))
                .andExpect(jsonPath("$.emailAvailable").value(false));
    }

    @Test
    void thresholdBoundsAreValidated() throws Exception {
        for (String invalid : new String[] {"0.09", "100.01", "1.234"}) {
            mvc.perform(put("/api/v1/me/notification-preferences")
                            .with(investor())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(prefs(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String valid : new String[] {"0.10", "100.00"}) {
            when(service.updatePreferences(eq(USER_ID), eq(false), eq(true), any()))
                    .thenReturn(new NotificationPreference(USER_ID, false, true, new BigDecimal(valid), Instant.now()));
            mvc.perform(put("/api/v1/me/notification-preferences")
                            .with(investor())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(prefs(valid)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void markReadOfOthersNotificationIs404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotificationNotFoundException(id)).when(service).markRead(USER_ID, id);

        mvc.perform(patch("/api/v1/me/notifications/" + id + "/read").with(investor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void markReadReturns204() throws Exception {
        mvc.perform(patch("/api/v1/me/notifications/" + UUID.randomUUID() + "/read")
                        .with(investor()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unreadCount() throws Exception {
        when(service.unreadCount(USER_ID)).thenReturn(4L);

        mvc.perform(get("/api/v1/me/notifications/unread-count").with(investor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());
        verify(service, never()).inbox(any(), any(Boolean.class), any());
    }
}
