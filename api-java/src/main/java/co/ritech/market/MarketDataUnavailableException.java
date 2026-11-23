package co.ritech.market;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public class MarketDataUnavailableException extends BusinessException {

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "MARKET_DATA_UNAVAILABLE", message, cause);
    }

    public MarketDataUnavailableException(String message) {
        this(message, null);
    }
}
