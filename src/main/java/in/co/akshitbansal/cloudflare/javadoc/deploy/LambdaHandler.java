package in.co.akshitbansal.cloudflare.javadoc.deploy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.MavenCentralClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.LambdaInput;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.CloudflareService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.FilesystemService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.IndexHtmlGeneratingService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.MavenCentralService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class LambdaHandler implements RequestHandler<LambdaInput, Void> {

    @Override
    public Void handleRequest(LambdaInput lambdaInput, Context context) {
        // Validate the input
        validateInput(lambdaInput);
        log.info("Found packages to scan: {}", lambdaInput.getPackages());

        // Get env variables
        boolean DISABLE_TEMP_FILE_DELETION = Boolean.parseBoolean(System.getenv("DISABLE_TEMP_FILE_DELETION"));
        boolean DISABLE_CLOUDFLARE_DEPLOYMENT = Boolean.parseBoolean(System.getenv("DISABLE_CLOUDFLARE_DEPLOYMENT"));
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
        try(ExecutorService executor = new MDCExecutorService(Executors.newVirtualThreadPerTaskExecutor())) {

            // Instantiating MavenCentralClient bean
            HttpClient httpClient = HttpClient.newHttpClient();
            String MAVEN_CENTRAL_BASE_URL = "https://repo1.maven.org/maven2";
            MavenCentralClient mavenCentralClient = new MavenCentralClient(httpClient, MAVEN_CENTRAL_BASE_URL);

            // Instantiating MavenCentralService bean
            MavenCentralService mavenCentralService = new MavenCentralService(executor, mavenCentralClient);

            // Instantiating IndexHtmlGeneratingService bean
            Configuration config = new Configuration(Configuration.VERSION_2_3_34);
            config.setClassLoaderForTemplateLoading(LambdaHandler.class.getClassLoader(), "");
            config.setDefaultEncoding("UTF-8");
            Template template = config.getTemplate("package-index.ftl");
            IndexHtmlGeneratingService indexHtmlGeneratingService = new IndexHtmlGeneratingService(executor, template);

            // Instantiating CloudflareService bean
            CloudflareService cloudflareService = new CloudflareService(CLOUDFLARE_API_TOKEN, CLOUDFLARE_PROJECT_NAME);

            // Instantiating FilesystemService bean
            FilesystemService filesystemService = new FilesystemService();

            // Fetch all artifacts for the given packages from Maven Central
            List<MavenArtifact> artifacts = mavenCentralService.getAllArtifacts(lambdaInput.getPackages());

            // Create a temporary directory to prepare the javadoc site bundle
            Path tempDir = Files.createTempDirectory("cloudflare-javadoc");
            String siteDir = tempDir.resolve("site").toString();
            log.info("Created temporary directory for javadoc site bundle: {}", siteDir);

            // Prepare the javadoc bundles for all artifacts in the temporary directory
            mavenCentralService.prepareJavadocBundles(siteDir, artifacts);

            // Generate index.html for the javadoc site
            indexHtmlGeneratingService.generateIndexHtml(siteDir, 3);

            // Deploy the generated javadoc site to Cloudflare Pages
            if(DISABLE_CLOUDFLARE_DEPLOYMENT) log.warn("Cloudflare deployment is disabled. Skipping deployment step.");
            else cloudflareService.deploy(siteDir, tempDir.toString());

            // Clean up the temporary directory
            if(DISABLE_TEMP_FILE_DELETION) log.warn("Temporary file deletion is disabled. Skipping deletion of temporary directory: {}", tempDir);
            else filesystemService.deleteDirectoryRecursively(tempDir.toString());
        }
        catch (Exception ex) {
            log.error("Failed to deploy javadoc site to Cloudflare Pages", ex);
            throw new RuntimeException("Failed to deploy javadoc site to Cloudflare Pages", ex);
        }
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
