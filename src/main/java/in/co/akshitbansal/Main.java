package in.co.akshitbansal;

import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.client.MavenCentralClient;
import in.co.akshitbansal.model.MavenArtifact;
import in.co.akshitbansal.model.MavenPackage;
import in.co.akshitbansal.service.CloudflareService;
import in.co.akshitbansal.service.FilesystemService;
import in.co.akshitbansal.service.IndexHtmlGeneratingService;
import in.co.akshitbansal.service.MavenCentralService;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class Main {

    public static void main(String[] args) {
        // Instantiating virtual thread pool
        try(ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Validate required system properties
            String packagesArg = System.getProperty("packages");
            if(packagesArg == null) {
                throw new IllegalArgumentException("Comma separated list of packages to scan must be provided as a system property with key 'packages'");
            }

            String CLOUDFLARE_API_TOKEN = System.getProperty("cloudflare.api-token");
            if(CLOUDFLARE_API_TOKEN == null) {
                throw new IllegalArgumentException("Cloudflare API token must be provided as system property with key 'cloudflare.api-token'");
            }

            String CLOUDFLARE_PROJECT_NAME = System.getProperty("cloudflare.project-name");
            if(CLOUDFLARE_PROJECT_NAME == null) {
                throw new IllegalArgumentException("Cloudflare Pages project name must be provided as system property with key 'cloudflare.project-name'");
            }

            // Instantiating MavenCentralClient bean
            HttpClient httpClient = HttpClient.newHttpClient();
            String MAVEN_CENTRAL_BASE_URL = "https://repo1.maven.org/maven2";
            MavenCentralClient mavenCentralClient = new MavenCentralClient(httpClient, MAVEN_CENTRAL_BASE_URL);

            // Instantiating MavenCentralService bean
            MavenCentralService mavenCentralService = new MavenCentralService(executor, mavenCentralClient);

            // Instantiating IndexHtmlGeneratingService bean
            Configuration config = new Configuration(Configuration.VERSION_2_3_34);
            config.setClassLoaderForTemplateLoading(Main.class.getClassLoader(), "");
            config.setDefaultEncoding("UTF-8");
            Template template = config.getTemplate("package-index.ftl");
            IndexHtmlGeneratingService indexHtmlGeneratingService = new IndexHtmlGeneratingService(executor, template);

            // Instantiating CloudflareService bean
            CloudflareService cloudflareService = new CloudflareService(CLOUDFLARE_API_TOKEN, CLOUDFLARE_PROJECT_NAME);

            // Instantiating FilesystemService bean
            FilesystemService filesystemService = new FilesystemService();

            // Running the main logic
            // Parse the packages from the system property
            List<MavenPackage> packages = Arrays
                    .stream(packagesArg.split(","))
                    .map(Main::parsePackage)
                    .toList();
            log.info("Found packages to scan: {}", packages);
            // Fetch all artifacts for the given packages from Maven Central
            List<MavenArtifact> artifacts = mavenCentralService.getAllArtifacts(packages);

            // Create a temporary directory to prepare the javadoc site bundle
            Path sitePath = Files.createTempDirectory("cloudflare-javadoc");
            String siteDir = sitePath.toString();
            log.info("Created temporary directory for javadoc site bundle: {}", siteDir);
            // Prepare the javadoc bundles for all artifacts in the temporary directory
            mavenCentralService.prepareJavadocBundles(siteDir, artifacts);
            // Generate index.html for the javadoc site
            indexHtmlGeneratingService.generateIndexHtml(siteDir, 2);
            // Deploy the generated javadoc site to Cloudflare Pages
            cloudflareService.deploy(siteDir);
            // Clean up the temporary directory
            filesystemService.deleteDirectoryRecursively(siteDir);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to deploy javadoc site to Cloudflare Pages", ex);
        }
    }

    private static MavenPackage parsePackage(String packageStr) {
        String[] parts = packageStr.split(":");
        if(parts.length != 2) {
            throw new IllegalArgumentException("Invalid package format: " + packageStr + ". Expected format is groupId:artifactId");
        }
        return new MavenPackage(parts[0], parts[1]);
    }
}