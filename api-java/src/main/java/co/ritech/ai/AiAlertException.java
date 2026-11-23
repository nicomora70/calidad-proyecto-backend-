package co.ritech.ai;

/** Internal failure of a generator. Never reaches the client: the alert is stored as FAILED. */
public class AiAlertException extends RuntimeException {

    public AiAlertException(String message) {
        super(message);
    }

    public AiAlertException(String message, Throwable cause) {
        super(message, cause);
    }
}
