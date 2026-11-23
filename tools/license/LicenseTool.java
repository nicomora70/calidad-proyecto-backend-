import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Vendor tool to create the key pair and issue signed RiTech licenses (format ritech-license-v1).
 * Pure JDK (17+), run without compiling:
 *
 *   java tools/license/LicenseTool.java keygen <dir>
 *   java tools/license/LicenseTool.java issue --key <private-key.pem> --company "Empresa S.A.S."
 *        --plan ENTERPRISE --max-users 25 --expires 2027-11-30 [--id RT-2026-001] [--issued 2026-11-23]
 *        --out license.json
 *
 * The private key must NEVER be committed or sent to a client. Only public-key.pem goes into
 * api-java/src/main/resources/license/.
 */
public class LicenseTool {

    private static final Set<String> PLANS = Set.of("BASIC", "PROFESSIONAL", "ENTERPRISE");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
        }
        switch (args[0]) {
            case "keygen" -> keygen(Path.of(args.length > 1 ? args[1] : "."));
            case "issue" -> issue(options(args));
            default -> usage();
        }
    }

    private static void keygen(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path privateFile = dir.resolve("private-key.pem");
        if (Files.exists(privateFile)) {
            fail("Refusing to overwrite " + privateFile);
        }
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.writeString(privateFile, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(dir.resolve("public-key.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        System.out.println("Keys written to " + dir.toAbsolutePath() + " (keep private-key.pem secret)");
    }

    private static void issue(Map<String, String> o) throws Exception {
        String plan = require(o, "plan").toUpperCase();
        if (!PLANS.contains(plan)) {
            fail("--plan must be one of " + PLANS);
        }
        int maxUsers = Integer.parseInt(require(o, "max-users"));
        LocalDate issued = LocalDate.parse(o.getOrDefault("issued", LocalDate.now().toString()));
        LocalDate expires = LocalDate.parse(require(o, "expires"));
        if (maxUsers < 1 || expires.isBefore(issued)) {
            fail("--max-users must be >= 1 and --expires on or after --issued");
        }
        String id = o.getOrDefault("id", "RT-" + issued.getYear() + "-" + System.currentTimeMillis() % 100000);

        String payload = "{\"licenseId\":" + json(id)
                + ",\"company\":" + json(require(o, "company"))
                + ",\"plan\":" + json(plan)
                + ",\"maxUsers\":" + maxUsers
                + ",\"issuedAt\":" + json(issued.toString())
                + ",\"expiresAt\":" + json(expires.toString()) + "}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(readPrivateKey(Path.of(require(o, "key"))));
        signer.update(payloadBytes);
        String file = "{\n  \"format\": \"ritech-license-v1\",\n  \"payload\": \""
                + Base64.getEncoder().encodeToString(payloadBytes) + "\",\n  \"signature\": \""
                + Base64.getEncoder().encodeToString(signer.sign()) + "\"\n}\n";

        Path out = Path.of(require(o, "out"));
        Files.writeString(out, file);
        System.out.println("License " + id + " (" + plan + ", " + maxUsers + " users, expires " + expires
                + ") written to " + out.toAbsolutePath());
    }

    private static PrivateKey readPrivateKey(Path pem) throws Exception {
        String base64 = Files.readString(pem)
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    private static String json(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                fail("Invalid option: " + args[i]);
            }
            o.put(args[i].substring(2), args[i + 1]);
        }
        return o;
    }

    private static String require(Map<String, String> o, String name) {
        String value = o.get(name);
        if (value == null || value.isBlank()) {
            fail("Missing --" + name);
        }
        return value;
    }

    private static void usage() {
        fail("Usage: LicenseTool keygen <dir> | LicenseTool issue --key <pem> --company <name> --plan "
                + "<BASIC|PROFESSIONAL|ENTERPRISE> --max-users <n> --expires <yyyy-mm-dd> [--id <id>] "
                + "[--issued <yyyy-mm-dd>] --out <file>");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
