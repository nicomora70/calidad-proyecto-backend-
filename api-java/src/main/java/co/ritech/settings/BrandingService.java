package co.ritech.settings;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.ritech.audit.AuditService;
import co.ritech.license.LicenseService;

/**
 * Per-installation look for the frontend. Values not set by the ADMIN fall back to the
 * defaults; the company name falls back to the one in the license.
 */
@Service
public class BrandingService {

    static final String PRODUCT_NAME = "branding.productName";
    static final String COMPANY_NAME = "branding.companyName";
    static final String PRIMARY_COLOR = "branding.primaryColor";
    static final String LOGO_URL = "branding.logoUrl";
    static final String LEGAL_NOTICE = "branding.legalNotice";
    private static final List<String> KEYS = List.of(PRODUCT_NAME, COMPANY_NAME, PRIMARY_COLOR, LOGO_URL, LEGAL_NOTICE);

    static final String DEFAULT_PRODUCT = "RiTech";
    static final String DEFAULT_COLOR = "#1F6FEB";
    static final String DEFAULT_LEGAL = "RiTech es una herramienta de simulación. Los resultados no constituyen "
            + "asesoría financiera ni garantizan rendimientos futuros.";

    private final SettingRepository settings;
    private final LicenseService license;
    private final AuditService audit;
    private final Clock clock;

    public BrandingService(SettingRepository settings, LicenseService license, AuditService audit, Clock clock) {
        this.settings = settings;
        this.license = license;
        this.audit = audit;
        this.clock = clock;
    }

    public record Branding(
            String productName, String companyName, String primaryColor, String logoUrl, String legalNotice) {}

    @Transactional(readOnly = true)
    public Branding get() {
        Map<String, String> values = settings.findAllById(KEYS).stream()
                .filter(s -> s.getValue() != null)
                .collect(Collectors.toMap(Setting::getKey, Setting::getValue));
        return new Branding(
                values.getOrDefault(PRODUCT_NAME, DEFAULT_PRODUCT),
                values.getOrDefault(COMPANY_NAME, license.company()),
                values.getOrDefault(PRIMARY_COLOR, DEFAULT_COLOR),
                values.get(LOGO_URL),
                values.getOrDefault(LEGAL_NOTICE, DEFAULT_LEGAL));
    }

    /** A null field removes the custom value (back to the default). */
    @Transactional
    public Branding update(UUID actorId, Branding branding) {
        var now = clock.instant();
        Map<String, String> values = new HashMap<>();
        values.put(PRODUCT_NAME, branding.productName());
        values.put(COMPANY_NAME, branding.companyName());
        values.put(PRIMARY_COLOR, branding.primaryColor());
        values.put(LOGO_URL, branding.logoUrl());
        values.put(LEGAL_NOTICE, branding.legalNotice());
        values.forEach((key, v) -> {
            if (v == null || v.isBlank()) {
                settings.deleteById(key);
            } else {
                settings.save(new Setting(key, v.strip(), now));
            }
        });
        audit.record(actorId, "BRANDING_UPDATED", "SETTINGS", null, null);
        return get();
    }
}
