package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.ses.SesClient;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

@Singleton
@Slf4j
public class ResourcesLifecycleManager {

    private final ExecutorService executor;
    private final SesClient sesClient;
    private final SchedulerClient schedulerClient;
    private final CloseableHttpClient apacheHttpClient;

    @Inject
    public ResourcesLifecycleManager(
            ExecutorService executor,
            SesClient sesClient,
            SchedulerClient schedulerClient,
            CloseableHttpClient apacheHttpClient
    ) {
        this.executor = executor;
        this.sesClient = sesClient;
        this.schedulerClient = schedulerClient;
        this.apacheHttpClient = apacheHttpClient;
    }

    @PreDestroy
    public void close() throws IOException {
        executor.close();
        sesClient.close();
        schedulerClient.close();
        apacheHttpClient.close();
        log.info("Closed all resources in ResourcesLifecycleManager");
    }
}
