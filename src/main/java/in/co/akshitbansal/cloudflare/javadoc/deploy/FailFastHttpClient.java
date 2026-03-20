package in.co.akshitbansal.cloudflare.javadoc.deploy;

import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.HttpStatusException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableHttpStatusException;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
// A wrapper around HttpClient that implements fail-fast behavior for HTTP requests
public class FailFastHttpClient extends HttpClient {

    private final HttpClient delegate;

    public static FailFastHttpClient newInstance(Consumer<Builder> builderConsumer) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        builderConsumer.accept(builder);
        return new FailFastHttpClient(builder.build());
    }

    public static FailFastHttpClient newInstance(HttpClient delegate) {
        return new FailFastHttpClient(delegate);
    }

    public static FailFastHttpClient newInstance() {
        return new FailFastHttpClient(HttpClient.newHttpClient());
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return delegate.newWebSocketBuilder();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public boolean awaitTermination(Duration duration) throws InterruptedException {
        return delegate.awaitTermination(duration);
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public void shutdownNow() {
        delegate.shutdownNow();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    // Throws RetryableException for IOExceptions and HttpStatusException for non-successful HTTP responses
    // If the HTTP response status code is 429, 408 or >= 500, it throws RetryableHttpStatusException (subclass of HttpStatusException) to indicate that the request can be retried
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        logRequestDetails(request);
        HttpResponse<T> response;
        try {
            response = delegate.send(request, responseBodyHandler);
        }
        catch (Exception ex) {
            handleException(ex, request);
            // The handleException method will always throw an exception, so this line should never be reached. Adding this to satisfy the compiler and to indicate that the code should never reach here.
            throw new IllegalStateException("Unreachable");
        }
        return handleResponse(request, response);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        logRequestDetails(request);
        return delegate
                .sendAsync(request, responseBodyHandler)
                .handle((response, th) -> handleAsyncResponse(request, response, th));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        logRequestDetails(request);
        return delegate
                .sendAsync(request, responseBodyHandler, pushPromiseHandler)
                .handle((response, th) -> handleAsyncResponse(request, response, th));
    }

    private <T> HttpResponse<T> handleAsyncResponse(HttpRequest request, HttpResponse<T> response, Throwable th) {
        // If the CompletableFuture completed exceptionally, handle the exception and throw appropriate retryable or non-retryable exceptions
        if(th != null) {
            handleException(th, request);
            // The handleException method will always throw an exception, so this line should never be reached. Adding this to satisfy the compiler and to indicate that the code should never reach here.
            throw new IllegalStateException("Unreachable");
        }

        // If the CompletableFuture completed normally, check the HTTP response status code and throw appropriate exceptions for non-successful responses
        return handleResponse(request, response);
    }

    private <T> HttpResponse<T> handleResponse(@NonNull HttpRequest request, @NonNull HttpResponse<T> response) {
        int statusCode = response.statusCode();
        if(statusCode >= 200 && statusCode < 300) {
            return response; // Successful response, return as is
        }
        if(statusCode == 429 || statusCode == 408 || statusCode >= 500) {
            throw new RetryableHttpStatusException(MessageFormat.format(
                    "Received retryable HTTP response with status code {0} for request to {1} {2}",
                    statusCode, request.method(), request.uri()
            ), response);
        }
        throw new HttpStatusException(MessageFormat.format(
                "Received non-successful HTTP response with status code {0} for request to {1} {2}",
                statusCode, request.method(), request.uri()
        ), response);
    }

    private void handleException(@NonNull Throwable th, @NonNull HttpRequest request) {
        Throwable unwrapped = unwrapThrowable(th);
        if(unwrapped instanceof IOException) {
            throw new RetryableException(MessageFormat.format(
                    "Retryable exception occurred while sending HTTP request to {0} {1}",
                    request.method(), request.uri()
            ), unwrapped);
        }
        if(unwrapped instanceof InterruptedException) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new RuntimeException(MessageFormat.format(
                    "Thread was interrupted while sending HTTP request to {0} {1}",
                    request.method(), request.uri()
            ), unwrapped);
        }
        if(unwrapped instanceof RuntimeException ex) {
            throw ex; // Rethrow runtime exceptions as is
        }
        throw new RuntimeException(MessageFormat.format(
                "Unexpected exception occurred while sending HTTP request to {0} {1}",
                request.method(), request.uri()
        ), unwrapped);
    }

    private Throwable unwrapThrowable(@NonNull Throwable th) {
        while(th instanceof CompletionException || th instanceof ExecutionException) {
            if(th.getCause() == null) break;
            th = th.getCause();
        }
        return th;
    }

    private void logRequestDetails(@NonNull HttpRequest request) {
        log.info("HTTP Request: {} {}", request.method(), request.uri());
    }
}
