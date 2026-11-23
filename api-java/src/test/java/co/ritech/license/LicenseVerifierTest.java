package co.ritech.license;

import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.ritech.license.LicenseExceptions.InvalidLicenseException;
import co.ritech.license.LicenseModel.LicenseFile;
import co.ritech.license.LicenseModel.Plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseVerifierTest {

    private static final LocalDate ISSUED = LocalDate.of(2026, 11, 23);
    private static final LocalDate EXPIRES = LocalDate.of(2027, 11, 30);

    private final TestLicenses licenses = new TestLicenses();
    private final LicenseVerifier verifier = new LicenseVerifier(licenses.publicKey(), new ObjectMapper());

    @Test
    void validLicenseIsDecoded() {
        var data = verifier.verify(licenses.issue("PROFESSIONAL", 10, ISSUED, EXPIRES));

        assertThat(data.company()).isEqualTo("Cliente S.A.S.");
        assertThat(data.plan()).isEqualTo(Plan.PROFESSIONAL);
        assertThat(data.maxUsers()).isEqualTo(10);
        assertThat(data.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    void tamperedPayloadIsRejected() {
        var genuine = licenses.issue("BASIC", 5, ISSUED, EXPIRES);
        String upgraded = Base64.getEncoder()
                .encodeToString(new String(Base64.getDecoder().decode(genuine.payload()))
                        .replace("BASIC", "ENTERPRISE")
                        .getBytes());
        var forged = new LicenseFile(genuine.format(), upgraded, genuine.signature());

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void licenseSignedByAnotherKeyIsRejected() {
        var other = new TestLicenses().issue("ENTERPRISE", 5, ISSUED, EXPIRES);
        assertThatThrownBy(() -> verifier.verify(other)).isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void unknownFormatAndGarbageAreRejected() {
        var genuine = licenses.issue("BASIC", 5, ISSUED, EXPIRES);
        assertThatThrownBy(() -> verifier.verify(new LicenseFile("v0", genuine.payload(), genuine.signature())))
                .isInstanceOf(InvalidLicenseException.class);
        assertThatThrownBy(() -> verifier.verify(new LicenseFile(LicenseVerifier.FORMAT, "%%%", "%%%")))
                .isInstanceOf(InvalidLicenseException.class);
        assertThatThrownBy(() -> verifier.verify(null)).isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void signedButInvalidContentIsRejected() {
        assertThatThrownBy(() -> verifier.verify(licenses.issue("BASIC", 0, ISSUED, EXPIRES)))
                .isInstanceOf(InvalidLicenseException.class);
        assertThatThrownBy(() -> verifier.verify(licenses.issue("GOLD", 5, ISSUED, EXPIRES)))
                .isInstanceOf(InvalidLicenseException.class);
        assertThatThrownBy(() -> verifier.verify(licenses.issue("BASIC", 5, EXPIRES, ISSUED)))
                .isInstanceOf(InvalidLicenseException.class);
        assertThatThrownBy(() -> verifier.verify(licenses.sign("{\"plan\":\"BASIC\"}")))
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("licenseId");
    }

    @Test
    void bundledVendorPublicKeyLoads() {
        assertThat(LicenseVerifier.readPublicKey(new ClassPathResource("license/public-key.pem"))
                        .getAlgorithm())
                .isIn("Ed25519", "EdDSA");
    }
}
