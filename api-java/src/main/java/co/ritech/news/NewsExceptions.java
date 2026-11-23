package co.ritech.news;

import org.springframework.http.HttpStatus;

import co.ritech.common.BusinessException;

public final class NewsExceptions {

    private NewsExceptions() {}

    public static class NewsUnavailableException extends BusinessException {
        public NewsUnavailableException(String message) {
            super(HttpStatus.BAD_GATEWAY, "NEWS_UNAVAILABLE", message);
        }

        public NewsUnavailableException(String message, Throwable cause) {
            super(HttpStatus.BAD_GATEWAY, "NEWS_UNAVAILABLE", message, cause);
        }
    }

    public static class NewsQuotaExceededException extends BusinessException {
        public NewsQuotaExceededException(int maxDailyCalls) {
            super(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "NEWS_QUOTA_EXCEEDED",
                    "Daily news refresh limit reached (" + maxDailyCalls + " calls)");
        }
    }
}
