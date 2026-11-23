package co.ritech.license;

import java.time.LocalDate;

/** Published by the daily license check; notifications deliver it to IT_MANAGER and ADMIN. */
public record LicenseExpiringSoon(String licenseId, String company, LocalDate expiresAt, long daysRemaining) {}
