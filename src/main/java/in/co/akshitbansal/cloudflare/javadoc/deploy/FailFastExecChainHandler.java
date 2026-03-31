package in.co.akshitbansal.cloudflare.javadoc.deploy;

import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.HttpStatusException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableHttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;

@Slf4j
public class FailFastExecChainHandler implements ExecChainHandler {

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, ExecChain.Scope scope, ExecChain chain) throws IOException, HttpException {
        logRequest(request);
        ClassicHttpResponse response = chain.proceed(request, scope);
        int statusCode = response.getCode();
        if(statusCode >= 200 && statusCode < 300) return response;

        // Close the response to prevent connection leaks
        response.close();
        if(statusCode >= 300 && statusCode < 400) {
            Header locationHeader = response.getHeader(HttpHeaders.LOCATION);
            String locationHeaderValue = locationHeader != null ? locationHeader.getValue() : null;
            throw new HttpStatusException(MessageFormat.format(
                    "Received unexpected redirect response status code: {0}, Location header: {1} while sending HTTP request to {2} {3}",
                    statusCode, locationHeaderValue, request.getMethod(), request.getRequestUri()
            ), response);
        }
        if(statusCode == 429 || statusCode == 408 || statusCode >= 500) {
            throw new RetryableHttpStatusException(MessageFormat.format(
                    "Received retryable HTTP response with status code {0} while sending HTTP request to {1} {2}",
                    statusCode, request.getMethod(), request.getRequestUri()
            ), response);
        }
        throw new HttpStatusException(MessageFormat.format(
                "Received non-successful HTTP response with status code {0} while sending HTTP request to {1} {2}",
                statusCode, request.getMethod(), request.getRequestUri()
        ), response);
    }

    private void logRequest(ClassicHttpRequest request) {
        try {
            log.info("HTTP Request: {} {}", request.getMethod(), request.getUri());
        }
        catch (URISyntaxException ex) {
            throw new RuntimeException("Failed to log HTTP request due to invalid URI syntax: " + ex.getMessage(), ex);
        }
    }
}
