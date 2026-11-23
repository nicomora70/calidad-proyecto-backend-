package co.ritech.license;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import co.ritech.license.LicenseModel.LicenseData;
import co.ritech.license.LicenseModel.LicenseFile;
import co.ritech.license.LicenseModel.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Every installed license is kept (renewal history); the newest one is in force. */
@Entity
@Table(name = "licenses")
public class InstalledLicense {

    @Id
    private UUID id;

    @Column(name = "license_id", nullable = false, length = 60)
    private String licenseId;

    @Column(nullable = false, length = 150)
    private String company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, columnDefinition = "text")
    private String signature;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @Column(name = "installed_by")
    private UUID installedBy;

    protected InstalledLicense() {}

    public InstalledLicense(LicenseData data, LicenseFile file, UUID installedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.licenseId = data.licenseId();
        this.company = data.company();
        this.plan = data.plan();
        this.maxUsers = data.maxUsers();
        this.issuedAt = data.issuedAt();
        this.expiresAt = data.expiresAt();
        this.payload = file.payload();
        this.signature = file.signature();
        this.installedAt = now;
        this.installedBy = installedBy;
    }

    /** The stored file, re-verified at every startup (the DB is not trusted blindly). */
    public LicenseFile toFile() {
        return new LicenseFile(LicenseVerifier.FORMAT, payload, signature);
    }

    public UUID getId() {
        return id;
    }

    public String getLicenseId() {
        return licenseId;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }
}
