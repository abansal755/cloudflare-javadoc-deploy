package in.co.akshitbansal.cloudflare.javadoc.deploy.exception;

import org.apache.hc.core5.http.ClassicHttpResponse;

// Exception to indicate that an HTTP request resulted in an error status code that is considered retryable
public class RetryableHttpStatusException extends HttpStatusException {

    public RetryableHttpStatusException(String message, ClassicHttpResponse response) {
        super(message, response);
    }

    public RetryableHttpStatusException(String message, ClassicHttpResponse response, Throwable cause) {
        super(message, response, cause);
    }
}
