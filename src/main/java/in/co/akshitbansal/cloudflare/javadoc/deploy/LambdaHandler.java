package in.co.akshitbansal.cloudflare.javadoc.deploy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.AppModule;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.ResourcesModule;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.RetryModule;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.LambdaInput;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.SchedulingService;
import lombok.Cleanup;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LambdaHandler implements RequestHandler<LambdaInput, Void> {

    @Override
    public Void handleRequest(LambdaInput input, Context context) {
        // Validate input
        validateInput(input);

        // Adding AWS request ID to MDC for better traceability in logs
        String awsRequestId = context.getAwsRequestId();
        MDC.put("awsRequestId", awsRequestId);

        // Instantiating thread pool with virtual threads
        @Cleanup ExecutorService executorService = new MDCExecutorService(Executors.newVirtualThreadPerTaskExecutor());

        // Create Guice injector with the application module and the properties
        // Production stage is used to ensure that singletons are eagerly initialized at startup of the Lambda function
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new AppModule(),
                new RetryModule(),
                new ResourcesModule(executorService)
        );

        SchedulingService schedulingService = injector.getInstance(SchedulingService.class);
        schedulingService.schedule(input, awsRequestId);
        return null;
    }

    private void validateInput(LambdaInput input) {
        if(input == null)
            throw new IllegalArgumentException("Input cannot be null");
        if(input.getDeploymentIds() == null)
            throw new IllegalArgumentException("Deployment IDs cannot be null");
    }
}
