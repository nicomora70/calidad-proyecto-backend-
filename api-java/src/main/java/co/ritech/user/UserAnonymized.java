package co.ritech.user;

import java.util.UUID;

/** Published inside the anonymization transaction so other modules delete their personal data. */
public record UserAnonymized(UUID userId) {}
