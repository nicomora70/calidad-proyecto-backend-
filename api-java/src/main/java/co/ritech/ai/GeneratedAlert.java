package co.ritech.ai;

/** Output of a generator. Token counts are 0 for providers that do not report usage. */
public record GeneratedAlert(
        String title, String content, String model, String promptVersion, int promptTokens, int completionTokens) {}
