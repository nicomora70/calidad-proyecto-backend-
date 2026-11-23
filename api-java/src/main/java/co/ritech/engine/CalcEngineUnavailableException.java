package co.ritech.engine;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public class CalcEngineUnavailableException extends BusinessException {

    public CalcEngineUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CALC_ENGINE_UNAVAILABLE", message, cause);
    }
}
