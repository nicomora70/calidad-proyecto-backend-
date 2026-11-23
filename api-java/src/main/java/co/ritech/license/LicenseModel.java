package co.ritech.license;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** License vocabulary: commercial plans, the modules each one enables and the decoded license. */
public final class LicenseModel {

    private LicenseModel() {}

    public enum Module {
        SIMULATIONS,
        DASHBOARD,
        AI_ALERTS,
        NEWS,
        NOTIFICATIONS,
        EXPORTS,
        PROJECTIONS
    }

    public enum Plan {
        BASIC(EnumSet.of(Module.SIMULATIONS, Module.DASHBOARD)),
        PROFESSIONAL(EnumSet.of(
                Module.SIMULATIONS,
                Module.DASHBOARD,
                Module.AI_ALERTS,
                Module.NEWS,
                Module.NOTIFICATIONS,
                Module.EXPORTS)),
        ENTERPRISE(EnumSet.allOf(Module.class));

        private final Set<Module> modules;

        Plan(Set<Module> modules) {
            this.modules = Set.copyOf(modules);
        }

        public Set<Module> modules() {
            return modules;
        }
    }

    /** DISABLED = license checks off (development, demo, CI). */
    public enum State {
        DISABLED,
        MISSING,
        ACTIVE,
        EXPIRING,
        EXPIRED
    }

    /** Signed content of a license file. Dates are inclusive calendar days. */
    public record LicenseData(
            String licenseId, String company, Plan plan, int maxUsers, LocalDate issuedAt, LocalDate expiresAt) {}

    /** File format "ritech-license-v1": base64 payload (JSON bytes) and its Ed25519 signature. */
    public record LicenseFile(String format, String payload, String signature) {}
}
