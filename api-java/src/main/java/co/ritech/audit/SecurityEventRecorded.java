package co.ritech.audit;

import java.util.UUID;

/** Security-relevant audit entry (ISO 27001 A.8.16), handled after the audit commits. */
public record SecurityEventRecorded(String action, UUID userId) {}
