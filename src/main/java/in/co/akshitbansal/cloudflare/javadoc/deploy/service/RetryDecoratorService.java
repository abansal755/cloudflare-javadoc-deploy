package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Singleton
@Slf4j
public class RetryDecoratorService {

    private final RetryRegistry retryRegistry;

    @Inject
    public RetryDecoratorService(RetryRegistry retryRegistry) {
        this.retryRegistry = retryRegistry;
    }

    public <T> T executeSupplierWithRetry(String name, Supplier<T> supplier) {
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(getRetry(name), supplier);
        return decoratedSupplier.get();
    }

    public void executeConsumerWithRetry(String name, Runnable runnable) {
        Runnable decoratedRunnable = Retry.decorateRunnable(getRetry(name), runnable);
        decoratedRunnable.run();
    }

    private Retry getRetry(String name) {
        Retry retry = retryRegistry.retry(name);
        Retry.EventPublisher publisher = retry.getEventPublisher();
        publisher.onRetry(event ->
                log.warn("Retry attempt #{} for {} due to: ", event.getNumberOfRetryAttempts(), event.getName(), event.getLastThrowable()));
        return retry;
    }
}
