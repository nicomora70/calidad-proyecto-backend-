package co.ritech.license;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstalledLicenseRepository extends JpaRepository<InstalledLicense, UUID> {

    Optional<InstalledLicense> findFirstByOrderByInstalledAtDesc();
}
