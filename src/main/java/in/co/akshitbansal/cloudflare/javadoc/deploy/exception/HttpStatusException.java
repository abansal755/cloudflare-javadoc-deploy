package in.co.akshitbansal.cloudflare.javadoc.deploy.exception;

import lombok.Getter;
import org.apache.hc.core5.http.ClassicHttpResponse;

@Getter
// Exception to indicate that an HTTP request resulted in an error status code
public class HttpStatusException extends RuntimeException {

    private final ClassicHttpResponse response;

    public HttpStatusException(String message, ClassicHttpResponse response) {
        super(message);
        this.response = response;
    }

    public HttpStatusException(String message, ClassicHttpResponse response, Throwable cause) {
        super(message, cause);
        this.response = response;
    }
}
