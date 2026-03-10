package in.co.akshitbansal.cloudflare.javadoc.deploy.exception;

public class HttpServerSideException extends RuntimeException {

    public HttpServerSideException(String message) {
        super(message);
    }

    public HttpServerSideException(String message, Throwable cause) {
        super(message, cause);
    }
}
