package co.ritech.ai;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public class AiAlertNotFoundException extends BusinessException {

    public AiAlertNotFoundException(UUID simulationId) {
        super(HttpStatus.NOT_FOUND, "AI_ALERT_NOT_FOUND", "Simulation " + simulationId + " has no AI alert");
    }
}
