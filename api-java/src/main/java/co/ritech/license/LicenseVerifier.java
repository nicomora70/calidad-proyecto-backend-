package co.ritech.license;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import org.springframework.core.io.Resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.ritech.license.LicenseExceptions.InvalidLicenseException;
import co.ritech.license.LicenseModel.LicenseData;
import co.ritech.license.LicenseModel.LicenseFile;
import co.ritech.license.LicenseModel.Plan;

/**
 * Verifies the Ed25519 signature of a license file against the vendor public key and
 * decodes the payload. The signature covers the exact payload bytes, so no JSON
 * canonicalization is needed. Only the vendor holds the private key (tools/license).
 */
public class LicenseVerifier {

    static final String FORMAT = "ritech-license-v1";

    private final PublicKey publicKey;
    private final ObjectMapper mapper;

    public LicenseVerifier(PublicKey publicKey, ObjectMapper mapper) {
        this.publicKey = publicKey;
        this.mapper = mapper;
    }

    public static PublicKey readPublicKey(Resource pem) {
        try (var in = pem.getInputStream()) {
            return parsePublicKey(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        } catch (IOException ex) {
            throw new UncheckedIOException("License public key not found: " + pem, ex);
        }
    }

    public static PublicKey parsePublicKey(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid Ed25519 public key", ex);
        }
    }

    public LicenseData verify(LicenseFile file) {
        if (file == null || !FORMAT.equals(file.format()) || file.payload() == null || file.signature() == null) {
            throw new InvalidLicenseException("Unknown license format (expected " + FORMAT + ")");
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = Base64.getDecoder().decode(file.payload());
            signature = Base64.getDecoder().decode(file.signature());
        } catch (IllegalArgumentException ex) {
            throw new InvalidLicenseException("License payload or signature is not valid base64", ex);
        }
        if (!signatureMatches(payload, signature)) {
            throw new InvalidLicenseException("License signature is not valid");
        }
        return decode(payload);
    }

    private boolean signatureMatches(byte[] payload, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            return verifier.verify(signature);
        } catch (GeneralSecurityException ex) {
            return false;
        }
    }

    private LicenseData decode(byte[] payload) {
        try {
            JsonNode json = mapper.readTree(payload);
            var data = new LicenseData(
                    required(json, "licenseId"),
                    required(json, "company"),
                    Plan.valueOf(required(json, "plan")),
                    json.path("maxUsers").asInt(0),
                    LocalDate.parse(required(json, "issuedAt")),
                    LocalDate.parse(required(json, "expiresAt")));
            if (data.maxUsers() < 1 || data.expiresAt().isBefore(data.issuedAt())) {
                throw new InvalidLicenseException("License has invalid limits or dates");
            }
            return data;
        } catch (IOException | IllegalArgumentException | DateTimeParseException ex) {
            throw new InvalidLicenseException("License payload is malformed", ex);
        }
    }

    private static String required(JsonNode json, String field) {
        String value = json.path(field).asText("").strip();
        if (value.isEmpty()) {
            throw new InvalidLicenseException("License field '" + field + "' is missing");
        }
        return value;
    }
}
