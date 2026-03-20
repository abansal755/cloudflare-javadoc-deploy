package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import in.co.akshitbansal.cloudflare.javadoc.deploy.annotation.Retry;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableHttpStatusException;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.time.Duration;

public class RetryModule extends AbstractModule {

    @Override
    protected void configure() {
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig
                .custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(3))
                .retryExceptions(RetryableException.class, RetryableHttpStatusException.class)
                .build()
        );
        RetryInterceptor interceptor = new RetryInterceptor(retryRegistry);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Retry.class), interceptor);
    }
}
