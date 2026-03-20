package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.ses.SesClient;

import java.util.concurrent.ExecutorService;

@Singleton
@Slf4j
public class ResourcesLifecycleManager {

    private final ExecutorService executor;
    private final SesClient sesClient;
    private final SchedulerClient schedulerClient;

    @Inject
    public ResourcesLifecycleManager(ExecutorService executor, SesClient sesClient, SchedulerClient schedulerClient) {
        this.executor = executor;
        this.sesClient = sesClient;
        this.schedulerClient = schedulerClient;
    }

    @PreDestroy
    public void close() {
        executor.close();
        sesClient.close();
        schedulerClient.close();
        log.info("Closed all resources in ResourcesLifecycleManager");
    }
}
