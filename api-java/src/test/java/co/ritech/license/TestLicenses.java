package co.ritech.license;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;

import co.ritech.license.LicenseModel.LicenseFile;

/** Signs test licenses with a throw-away key pair (the vendor key is never used in tests). */
public final class TestLicenses {

    private final KeyPair keys;

    public TestLicenses() {
        try {
            this.keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public java.security.PublicKey publicKey() {
        return keys.getPublic();
    }

    /** Writes the public key as PEM so a Spring context can load it from a file: location. */
    public Path writePublicKey() {
        try {
            Path file = Files.createTempFile("ritech-test-license", ".pem");
            Files.writeString(
                    file,
                    "-----BEGIN PUBLIC KEY-----\n"
                            + Base64.getEncoder()
                                    .encodeToString(keys.getPublic().getEncoded())
                            + "\n-----END PUBLIC KEY-----\n");
            file.toFile().deleteOnExit();
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public LicenseFile issue(String plan, int maxUsers, LocalDate issuedAt, LocalDate expiresAt) {
        return sign("{\"licenseId\":\"RT-TEST-1\",\"company\":\"Cliente S.A.S.\",\"plan\":\"" + plan
                + "\",\"maxUsers\":" + maxUsers + ",\"issuedAt\":\"" + issuedAt + "\",\"expiresAt\":\"" + expiresAt
                + "\"}");
    }

    public LicenseFile sign(String payloadJson) {
        try {
            byte[] payload = payloadJson.getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keys.getPrivate());
            signer.update(payload);
            return new LicenseFile(
                    LicenseVerifier.FORMAT,
                    Base64.getEncoder().encodeToString(payload),
                    Base64.getEncoder().encodeToString(signer.sign()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** JSON body for POST /api/v1/license. */
    public static String json(LicenseFile file) {
        return "{\"format\":\"" + file.format() + "\",\"payload\":\"" + file.payload() + "\",\"signature\":\""
                + file.signature() + "\"}";
    }
}
