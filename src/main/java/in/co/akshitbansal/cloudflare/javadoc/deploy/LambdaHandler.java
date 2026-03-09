package in.co.akshitbansal.cloudflare.javadoc.deploy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.MavenCentralClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.LambdaInput;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.*;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        // Instantiating virtual thread pool
        @Cleanup ExecutorService executor = new MDCExecutorService(Executors.newVirtualThreadPerTaskExecutor());

        // Instantiating MavenCentralClient bean
        HttpClient httpClient = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL) // Always follow redirects, except from HTTPS URLs to HTTP URLs
                .build();

        List<MavenRepository> repositories = new ArrayList<>();
        // For stable releases
        repositories.add(new MavenRepository("https://repo1.maven.org/maven2", false));
        // For snapshots
        if(!DISABLE_SNAPSHOTS)
            repositories.add(new MavenRepository("https://central.sonatype.com/repository/maven-snapshots", true));
        MavenCentralClient mavenCentralClient = new MavenCentralClient(httpClient, repositories);

        // Instantiating MavenCentralService bean
        MavenCentralService mavenCentralService = new MavenCentralService(executor, mavenCentralClient);

        // Instantiating IndexHtmlGeneratingService bean
        Configuration config = new Configuration(Configuration.VERSION_2_3_34);
        config.setClassLoaderForTemplateLoading(LambdaHandler.class.getClassLoader(), "");
        config.setDefaultEncoding("UTF-8");
        Template template;
        try {
            template = config.getTemplate("package-index.ftl");
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to load freemarker template: package-index.ftl", ex);
        }
        IndexHtmlGeneratingService indexHtmlGeneratingService = new IndexHtmlGeneratingService(executor, template);

        // Instantiating CloudflareService bean
        CloudflareService cloudflareService = new CloudflareService(CLOUDFLARE_API_TOKEN, CLOUDFLARE_PROJECT_NAME);

        // Instantiating FilesystemService bean
        FilesystemService filesystemService = new FilesystemService();

        // Instantiating DeploymentService bean
        DeploymentService deploymentService = new DeploymentService(
                mavenCentralService,
                indexHtmlGeneratingService,
                cloudflareService,
                filesystemService,
                DISABLE_CLOUDFLARE_DEPLOYMENT,
                DISABLE_TEMP_FILE_DELETION
        );

        // Trigger the deployment process
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
