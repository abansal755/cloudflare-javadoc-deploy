package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.MDCExecutorService;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.ses.SesClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
@Getter
public class ResourcesService {

    private final ExecutorService executor;
    private final SesClient sesClient;
    private final SchedulerClient schedulerClient;

    @Inject
    public ResourcesService(AwsCredentialsProvider awsCredentialsProvider) {
        this.executor = new MDCExecutorService(Executors.newVirtualThreadPerTaskExecutor());
        this.sesClient = SesClient
                .builder()
                .region(Region.AP_SOUTH_2)
                .credentialsProvider(awsCredentialsProvider)
                .build();
        this.schedulerClient = SchedulerClient
                .builder()
                .region(Region.AP_SOUTH_2)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @PreDestroy
    public void close() {
        executor.close();
        sesClient.close();
        schedulerClient.close();
    }
}
