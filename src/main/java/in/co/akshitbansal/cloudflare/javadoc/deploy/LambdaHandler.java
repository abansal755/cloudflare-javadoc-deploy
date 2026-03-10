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
        boolean DISABLE_TEMP_FILE_DELETION = Boolean.parseBoolean(System.getenv("DISABLE_TEMP_FILE_DELETION"));
        boolean DISABLE_CLOUDFLARE_DEPLOYMENT = Boolean.parseBoolean(System.getenv("DISABLE_CLOUDFLARE_DEPLOYMENT"));
        boolean DISABLE_SNAPSHOTS = Boolean.parseBoolean(System.getenv("DISABLE_SNAPSHOTS"));
        String CLOUDFLARE_API_TOKEN = System.getenv("CLOUDFLARE_API_TOKEN");
        if(!DISABLE_CLOUDFLARE_DEPLOYMENT && CLOUDFLARE_API_TOKEN == null) {
            throw new IllegalArgumentException("Cloudflare API token must be provided as environment variable with key 'CLOUDFLARE_API_TOKEN'");
        }
        String CLOUDFLARE_PROJECT_NAME = System.getenv("CLOUDFLARE_PROJECT_NAME");
        if(!DISABLE_CLOUDFLARE_DEPLOYMENT && CLOUDFLARE_PROJECT_NAME == null) {
            throw new IllegalArgumentException("Cloudflare project name must be provided as system property with key 'CLOUDFLARE_PROJECT_NAME'");
        }

        // Adding AWS request ID to MDC for better traceability in logs
        MDC.put("awsRequestId", context.getAwsRequestId());

        // Container for properties to be injected into Guice modules
        Props props = new Props(
                DISABLE_SNAPSHOTS,
                DISABLE_CLOUDFLARE_DEPLOYMENT,
                DISABLE_TEMP_FILE_DELETION,
                CLOUDFLARE_API_TOKEN,
                CLOUDFLARE_PROJECT_NAME
        );

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
