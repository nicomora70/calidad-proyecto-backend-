package co.ritech.settings;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.ritech.audit.AuditService;
import co.ritech.license.LicenseService;
import co.ritech.settings.BrandingService.Branding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrandingServiceTest {

    private final Instant now = Instant.parse("2026-11-23T15:00:00Z");
    private SettingRepository settings;
    private LicenseService license;
    private BrandingService service;

    @BeforeEach
    void setUp() {
        settings = mock(SettingRepository.class);
        license = mock(LicenseService.class);
        when(settings.findAllById(any())).thenReturn(List.of());
        service = new BrandingService(settings, license, mock(AuditService.class), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void defaultsAndCompanyFromLicense() {
        when(license.company()).thenReturn("Cliente S.A.S.");

        Branding b = service.get();

        assertThat(b.productName()).isEqualTo("RiTech");
        assertThat(b.companyName()).isEqualTo("Cliente S.A.S.");
        assertThat(b.primaryColor()).isEqualTo(BrandingService.DEFAULT_COLOR);
        assertThat(b.logoUrl()).isNull();
        assertThat(b.legalNotice()).contains("no constituyen asesoría financiera");
    }

    @Test
    void storedValuesOverrideDefaults() {
        when(settings.findAllById(any()))
                .thenReturn(List.of(
                        new Setting(BrandingService.COMPANY_NAME, "Otra", now),
                        new Setting(BrandingService.PRIMARY_COLOR, "#000000", now)));
        when(license.company()).thenReturn("Cliente S.A.S.");

        Branding b = service.get();

        assertThat(b.companyName()).isEqualTo("Otra");
        assertThat(b.primaryColor()).isEqualTo("#000000");
    }

    @Test
    void blankFieldsAreRemovedAndOthersSaved() {
        service.update(null, new Branding("Mi Marca ", null, "#112233", "", null));

        verify(settings)
                .save(argThat(s -> s.getKey().equals(BrandingService.PRODUCT_NAME)
                        && s.getValue().equals("Mi Marca")));
        verify(settings).deleteById(BrandingService.COMPANY_NAME);
        verify(settings).deleteById(BrandingService.LOGO_URL);
    }
}
