package in.co.akshitbansal.cloudflare.javadoc.deploy.exception;

// Generic exception to indicate that an operation can be retried
public class RetryableException extends RuntimeException {

    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
