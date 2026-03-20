package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.MavenCentralClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.enums.DeploymentStatus;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.LambdaInput;
import jakarta.inject.Named;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;
import tools.jackson.databind.ObjectMapper;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Singleton
@Slf4j
public class SchedulingService {

    private final ExecutorService executor;
    private final MavenCentralClient mavenCentralClient;
    private final DeploymentService deploymentService;
    private final SchedulerClient schedulerClient;
    private final ObjectMapper objectMapper;

    private final String ROLE_ARN;
    private final String FUNCTION_ARN;
    private final String GROUP_NAME;

    @Inject
    public SchedulingService(
            ResourcesService resourcesService,
            MavenCentralClient mavenCentralClient,
            DeploymentService deploymentService,
            ObjectMapper objectMapper,
            @Named("stage.schedule.role-arn") String ROLE_ARN,
            @Named("stage.schedule.lambda-arn") String FUNCTION_ARN,
            @Named("stage.schedule.group-name") String GROUP_NAME
    ) {
        this.executor = resourcesService.getExecutor();
        this.mavenCentralClient = mavenCentralClient;
        this.deploymentService = deploymentService;
        this.schedulerClient = resourcesService.getSchedulerClient();
        this.objectMapper = objectMapper;
        this.ROLE_ARN = ROLE_ARN;
        this.FUNCTION_ARN = FUNCTION_ARN;
        this.GROUP_NAME = GROUP_NAME;
    }

    public void schedule(@NonNull LambdaInput input, @NonNull String awsRequestId) {
        // Check deployment status for all deploymentIds in parallel
        List<String> deploymentIds = input.getDeploymentIds();
        log.info("Checking deployment status for deploymentIds: {}", deploymentIds);
        List<CompletableFuture<DeploymentStatus>> futures = deploymentIds
                .stream()
                .map(deploymentId -> CompletableFuture.supplyAsync(
                        () -> mavenCentralClient.getDeploymentStatus(deploymentId),
                        executor
                ))
                .toList();

        // Wait for all deployment status checks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<DeploymentStatus> statuses = futures
                .stream()
                .map(CompletableFuture::join)
                .toList();
        log.info("Found deployment statuses: {}", statuses);

        // Check if any deployment is not published
        boolean anyNonPublished = futures
                .stream()
                .map(CompletableFuture::join)
                .anyMatch(status -> status != DeploymentStatus.PUBLISHED);

        if(!anyNonPublished) {
            // Run cloudflare deployment if all deployments are published
            log.info("All deployments are published. Starting Cloudflare deployment");
            deploymentService.deploy(awsRequestId);
            return;
        }
        // schedule self after 5 mins if any deployment is not published
        log.info("Not all deployments are published. Scheduling a re-check after 5 minutes");
        String scheduleExpression = getScheduleExpression();
        CreateScheduleRequest request = CreateScheduleRequest
                .builder()
                .name("cloudflare-javadoc-deploy-" + awsRequestId)
                .scheduleExpression(scheduleExpression)
                .scheduleExpressionTimezone("UTC")
                .flexibleTimeWindow(builder -> builder.mode(FlexibleTimeWindowMode.OFF))
                .target(builder -> builder
                        .arn(FUNCTION_ARN)
                        .roleArn(ROLE_ARN)
                        .input(objectMapper.writeValueAsString(input))
                )
                .groupName(GROUP_NAME)
                .build();
        CreateScheduleResponse response = schedulerClient.createSchedule(request);
        log.info("Created schedule with ARN: {} and expression: {}", response.scheduleArn(), scheduleExpression);
    }

    private String getScheduleExpression() {
        Instant instant = Instant
                .now()
                .plus(5, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return MessageFormat.format("at({0})", localDateTime.format(formatter));
    }
}
