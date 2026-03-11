package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

@Slf4j
@RequiredArgsConstructor
public class RetryInterceptor implements MethodInterceptor {

    private final RetryRegistry retryRegistry;

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Exception {
        Method method = methodInvocation.getMethod();
        Retry retry = retryRegistry.retry(method.getName());
        Retry.EventPublisher publisher = retry.getEventPublisher();
        publisher.onRetry(event ->
                log.warn("Retry attempt #{} for {} due to: ", event.getNumberOfRetryAttempts(), event.getName(), event.getLastThrowable()));

        Callable<Object> callable = Retry.decorateCallable(retry, () -> {
            try {
                return methodInvocation.proceed();
            }
            catch (Throwable th) {
                if(th instanceof Exception ex) throw ex;
                throw new Exception(th);
            }
        });
        return callable.call();
    }
}
