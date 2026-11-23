package co.ritech.ai;

import java.util.UUID;

/** Published when an alert reaches DONE (consumed by notifications). */
public record AiAlertReady(UUID userId, UUID simulationId, String title) {}
