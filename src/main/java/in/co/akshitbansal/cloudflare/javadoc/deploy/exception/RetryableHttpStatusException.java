package in.co.akshitbansal.cloudflare.javadoc.deploy.exception;

import java.net.http.HttpResponse;

// Exception to indicate that an HTTP request resulted in an error status code that is considered retryable
public class RetryableHttpStatusException extends HttpStatusException {

    public RetryableHttpStatusException(String message, HttpResponse<?> response) {
        super(message, response);
    }

    public RetryableHttpStatusException(String message, HttpResponse<?> response, Throwable cause) {
        super(message, response, cause);
    }
}
