package co.ritech.ai;

import java.util.UUID;

/** Published inside the simulation transaction; handled only after it commits. */
public record AiAlertRequested(UUID alertId, AlertContext context) {}
