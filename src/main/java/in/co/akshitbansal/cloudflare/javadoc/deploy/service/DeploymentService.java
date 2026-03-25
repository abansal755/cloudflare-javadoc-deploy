package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Singleton
@Slf4j
public class DeploymentService {

    private final MavenCentralService mavenCentralService;
    private final IndexHtmlGeneratingService indexHtmlGeneratingService;
    private final CloudflareService cloudflareService;
    private final FilesystemService filesystemService;
    private final AwsSesEmailService awsSesEmailService;

    private final boolean DISABLE_CLOUDFLARE_DEPLOYMENT;
    private final boolean DISABLE_TEMP_FILE_DELETION;
    private final boolean DISABLE_STATUS_EMAIL;

    private final List<MavenPackage> packages;

    @Inject
    public DeploymentService(
            MavenCentralService mavenCentralService,
            IndexHtmlGeneratingService indexHtmlGeneratingService,
            CloudflareService cloudflareService,
            FilesystemService filesystemService,
            AwsSesEmailService awsSesEmailService,
            Config config
    ) {
        this.mavenCentralService = mavenCentralService;
        this.indexHtmlGeneratingService = indexHtmlGeneratingService;
        this.cloudflareService = cloudflareService;
        this.filesystemService = filesystemService;
        this.awsSesEmailService = awsSesEmailService;
        this.DISABLE_CLOUDFLARE_DEPLOYMENT = config.getBoolean("stage.cloudflare-deployment.disabled");
        this.DISABLE_TEMP_FILE_DELETION = config.getBoolean("stage.temp-file-deletion.disabled");
        this.DISABLE_STATUS_EMAIL = config.getBoolean("stage.status-email.disabled");

        this.packages = Arrays
                .stream(config.getString("package.include").split(","))
                .map(this::parsePackage)
                .toList();
    }

    public void deploy(@NonNull String awsRequestId, @NonNull UUID correlationId) {
        boolean deploymentSuccess = true;
        String errorMessage = null;
        List<MavenArtifact> artifacts = new ArrayList<>();

        try {
            // Fetch all artifacts for the given packages from Maven Central
            log.info("Found packages to scan: {}", packages);
            artifacts = mavenCentralService.getAllArtifacts(packages);

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
            deploymentSuccess = false;
            errorMessage = ex.getMessage();
            throw new RuntimeException("Failed to deploy javadoc site to Cloudflare Pages", ex);
        }
        finally {
            // Send deployment status email
            if(DISABLE_STATUS_EMAIL) log.warn("Status email sending is disabled. Skipping sending deployment status email.");
            else awsSesEmailService.sendDeploymentStatusEmail(deploymentSuccess, errorMessage, packages, artifacts, awsRequestId, correlationId);
        }
    }

    private MavenPackage parsePackage(String str) {
        String[] parts = str.split(":");
        if(parts.length != 2) {
            throw new IllegalArgumentException("Invalid package format: " + str + ". Expected format: groupId:artifactId");
        }
        return new MavenPackage(parts[0], parts[1]);
    }
}
