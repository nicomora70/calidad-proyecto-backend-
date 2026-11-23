package co.ritech.simulation;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public class InsufficientCapitalException extends BusinessException {

    public InsufficientCapitalException(BigDecimal provided, BigDecimal minimum, String currency) {
        super(HttpStatus.BAD_REQUEST, "INSUFFICIENT_CAPITAL",
                "Initial capital %s %s is below the minimum of %s %s"
                        .formatted(provided.toPlainString(), currency, minimum.toPlainString(), currency));
    }
}
