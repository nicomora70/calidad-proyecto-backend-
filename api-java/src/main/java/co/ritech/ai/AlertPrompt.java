package co.ritech.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/** Versioned prompt (resources/prompts). Changing the text means a new version file. */
final class AlertPrompt {

    static final String VERSION = "alert-v1";
    private static final String TEMPLATE = load("prompts/" + VERSION + ".txt");

    private AlertPrompt() {}

    static String render(AlertContext c) {
        return TEMPLATE.replace("{{symbol}}", c.symbol())
                .replace("{{marketDate}}", c.marketDate().toString())
                .replace("{{variationPct}}", c.variationPct().toPlainString())
                .replace("{{leverage}}", c.leverage().toPlainString())
                .replace("{{returnPct}}", c.returnPct().toPlainString())
                .replace("{{initialCapital}}", c.initialCapital().toPlainString())
                .replace("{{currency}}", c.currency())
                .replace("{{finalAmount}}", c.finalAmount().toPlainString())
                .replace("{{profit}}", c.profit().toPlainString());
    }

    private static String load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Prompt not found: " + path, ex);
        }
    }
}
