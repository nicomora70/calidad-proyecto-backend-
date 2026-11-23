package co.ritech.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** AI alert of one simulation. Created PENDING with the simulation, completed asynchronously. */
@Entity
@Table(name = "ai_alerts")
public class AiAlert {

    private static final int MAX_TITLE = 200;
    private static final int MAX_REASON = 300;

    @Id
    private UUID id;

    @Column(name = "simulation_id", nullable = false, updatable = false)
    private UUID simulationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiAlertStatus status;

    @Column(length = MAX_TITLE)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 60)
    private String model;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "failure_reason", length = MAX_REASON)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiAlert() {}

    public AiAlert(UUID simulationId, UUID userId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.simulationId = simulationId;
        this.userId = userId;
        this.status = AiAlertStatus.PENDING;
        this.createdAt = createdAt;
    }

    void markDone(GeneratedAlert generated, BigDecimal cost, Instant now) {
        this.status = AiAlertStatus.DONE;
        this.title = truncate(generated.title(), MAX_TITLE);
        this.content = generated.content();
        this.model = generated.model();
        this.promptVersion = generated.promptVersion();
        this.promptTokens = generated.promptTokens();
        this.completionTokens = generated.completionTokens();
        this.costUsd = cost;
        this.completedAt = now;
    }

    void markFailed(String reason, Instant now) {
        this.status = AiAlertStatus.FAILED;
        this.failureReason = truncate(reason, MAX_REASON);
        this.completedAt = now;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AiAlertStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
