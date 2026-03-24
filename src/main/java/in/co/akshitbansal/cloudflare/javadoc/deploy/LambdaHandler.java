package in.co.akshitbansal.cloudflare.javadoc.deploy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.inject.Guice;
import com.google.inject.Stage;
import com.mycila.guice.ext.closeable.CloseableInjector;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.AppModule;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.RetryModule;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.LambdaInput;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.SchedulingService;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

@Slf4j
public class LambdaHandler implements RequestHandler<LambdaInput, Void> {

    @Override
    public Void handleRequest(LambdaInput input, Context context) {
        // Validate input
        validateInput(input);

        // Use correlation ID from input if available, otherwise generate a new one
        boolean isNewCorrelationId = false; // Flag to track if a new correlation ID was generated
        if(input.getCorrelationId() == null) {
            isNewCorrelationId = true;
            input.setCorrelationId(UUID.randomUUID());
        }

        // Put AWS request ID and correlation ID in MDC for logging
        String awsRequestId = context.getAwsRequestId();
        MDC.put("awsRequestId", awsRequestId);
        MDC.put("correlationId", input.getCorrelationId().toString());

        // Log if a new correlation ID was generated
        if(isNewCorrelationId) log.info("No correlation ID provided in input, generated a new one");

        // Create Guice injector with the application module and the properties
        // Production stage is used to ensure that singletons are eagerly initialized at startup of the Lambda function
        @Cleanup CloseableInjector injector = Guice
                .createInjector(
                        Stage.PRODUCTION,
                        new CloseableModule(),
                        new Jsr250Module(),
                        new AppModule(),
                        new RetryModule())
                .getInstance(CloseableInjector.class);

        SchedulingService schedulingService = injector.getInstance(SchedulingService.class);
        schedulingService.schedule(input, awsRequestId, input.getCorrelationId());
        return null;
    }

    private void validateInput(LambdaInput input) {
        if(input == null)
            throw new IllegalArgumentException("Input cannot be null");
        if(input.getDeploymentIds() == null)
            throw new IllegalArgumentException("Deployment IDs cannot be null");
    }
}
