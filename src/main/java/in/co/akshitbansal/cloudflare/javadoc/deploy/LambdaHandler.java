package in.co.akshitbansal.cloudflare.javadoc.deploy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.inject.Guice;
import com.google.inject.Injector;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.AppModule;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.Props;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.LambdaInput;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.DeploymentService;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
public class LambdaHandler implements RequestHandler<LambdaInput, Void> {

    @Override
    public Void handleRequest(LambdaInput lambdaInput, Context context) {
        // Validate the input
        validateInput(lambdaInput);

        // Get env variables
        Props props = Props.fromEnvVariables();

        // Adding AWS request ID to MDC for better traceability in logs
        MDC.put("awsRequestId", context.getAwsRequestId());

        // Create Guice injector with the application module and the properties
        Injector injector = Guice.createInjector(new AppModule(props));
        // Get the ExecutorService instance from Guice and ensure it gets properly shut down after the request is processed
        @Cleanup ExecutorService executorService = injector.getInstance(ExecutorService.class);

        DeploymentService deploymentService = injector.getInstance(DeploymentService.class);
        deploymentService.deploy(lambdaInput.getPackages());
        return null;
    }

    private void validateInput(LambdaInput lambdaInput) {
        if(lambdaInput == null)
            throw new IllegalArgumentException("Input cannot be null");
        List<MavenPackage> packages = lambdaInput.getPackages();
        if(packages == null || packages.isEmpty())
            throw new IllegalArgumentException("At least one package must be provided in the input");
        packages.forEach(mavenPackage -> {
            if(mavenPackage.getGroupId() == null || mavenPackage.getGroupId().isBlank())
                throw new IllegalArgumentException("Group ID cannot be null or blank for package: " + mavenPackage);
            if(mavenPackage.getArtifactId() == null || mavenPackage.getArtifactId().isBlank())
                throw new IllegalArgumentException("Artifact ID cannot be null or blank for package: " + mavenPackage);
        });
    }
}
