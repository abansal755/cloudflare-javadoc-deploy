package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@RequiredArgsConstructor
@Slf4j
public class RetryDecoratorService {

    private final RetryRegistry retryRegistry;

    public <T> T executeSupplierWithRetry(String name, Supplier<T> supplier) {
        Retry retry = retryRegistry.retry(name);
        Retry.EventPublisher publisher = retry.getEventPublisher();
        publisher.onRetry(event ->
                log.warn("Retry attempt #{} for {} due to: ", event.getNumberOfRetryAttempts(), event.getName(), event.getLastThrowable()));
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, supplier);
        return decoratedSupplier.get();
    }
}
