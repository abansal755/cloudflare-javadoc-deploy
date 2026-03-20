package in.co.akshitbansal.cloudflare.javadoc.deploy.exception;

import lombok.Getter;

import java.net.http.HttpResponse;

@Getter
// Exception to indicate that an HTTP request resulted in an error status code
public class HttpStatusException extends RuntimeException {

    private final HttpResponse<?> response;

    public HttpStatusException(String message, HttpResponse<?> response) {
        super(message);
        this.response = response;
    }

    public HttpStatusException(String message, HttpResponse<?> response, Throwable cause) {
        super(message, cause);
        this.response = response;
    }
}
