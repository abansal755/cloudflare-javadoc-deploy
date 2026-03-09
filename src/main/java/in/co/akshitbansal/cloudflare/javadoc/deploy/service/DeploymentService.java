package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class DeploymentService {

    private final MavenCentralService mavenCentralService;
    private final IndexHtmlGeneratingService indexHtmlGeneratingService;
    private final CloudflareService cloudflareService;
    private final FilesystemService filesystemService;

    private final boolean DISABLE_CLOUDFLARE_DEPLOYMENT;
    private final boolean DISABLE_TEMP_FILE_DELETION;

    public void deploy(@NonNull List<MavenPackage> packages) {
        try {
            // Fetch all artifacts for the given packages from Maven Central
            log.info("Found packages to scan: {}", packages);
            List<MavenArtifact> artifacts = mavenCentralService.getAllArtifacts(packages);

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
            throw new RuntimeException("Failed to deploy javadoc site to Cloudflare Pages", ex);
        }
    }
}
