package in.co.akshitbansal.cloudflare.javadoc.deploy;

import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.*;

import java.io.IOException;
import java.text.MessageFormat;

@Slf4j
public class FailFastExecChainHandler implements ExecChainHandler {

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, ExecChain.Scope scope, ExecChain chain) throws IOException, HttpException {
        log.info("HTTP Request: {} {}", request.getMethod(), request.getRequestUri());
        ClassicHttpResponse response = chain.proceed(request, scope);

        int statusCode = response.getCode();
        Header locationHeader = response.getHeader(HttpHeaders.LOCATION);
        String locationHeaderValue = locationHeader != null ? locationHeader.getValue() : null;

        if(statusCode >= 300 && statusCode < 400) {
            // Close the response to prevent connection leaks, as we are not going to follow the redirect
            response.close();
            throw new IllegalStateException(MessageFormat.format(
                    "Received unexpected redirect response status code: {0}, Location header: {1} while sending HTTP request to {2} {3}",
                    statusCode, locationHeaderValue, request.getMethod(), request.getRequestUri()
            ));
        }
        if(statusCode == 429 || statusCode == 408 || statusCode >= 500) {
            // Close the response to prevent connection leaks, as the request will be retried
            response.close();
            throw new RetryableException(MessageFormat.format(
                    "Received retryable HTTP response with status code {0} while sending HTTP request to {1} {2}",
                    statusCode, request.getMethod(), request.getRequestUri()
            ));
        }
        return response;
    }
}
