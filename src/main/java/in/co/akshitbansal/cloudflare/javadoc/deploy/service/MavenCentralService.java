package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.MavenCentralClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Singleton
@Slf4j
public class MavenCentralService {

    private final MavenCentralClient mavenCentralClient;
    private final ExecutorService executor;

    @Inject
    public MavenCentralService(MavenCentralClient mavenCentralClient, ExecutorService executor) {
        this.mavenCentralClient = mavenCentralClient;
        this.executor = executor;
    }

    public List<MavenArtifact> getAllArtifacts(@NonNull List<MavenPackage> packages) {
        try {
            log.info("Started fetching artifact versions from Maven Central");
            List<CompletableFuture<List<MavenArtifact>>> futures = packages
                    .stream()
                    .map(artifact -> CompletableFuture.supplyAsync(() -> mavenCentralClient
                            .getArtifacts(artifact)
                            .stream()
                            .toList(), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<MavenArtifact> artifacts = futures
                    .stream()
                    .flatMap(future -> future.join().stream())
                    .toList();
            log.info("Completed fetching artifact versions from Maven Central");
            return artifacts;
        }
        catch (Exception ex) {
            throw new RuntimeException(MessageFormat.format(
                    "Failed to fetch artifacts for packages: {0}", packages
            ), ex);
        }
    }

    public void prepareJavadocBundles(@NonNull String sitePath, @NonNull List<MavenArtifact> artifacts) {
        try {
            log.info("Started preparing javadoc site bundles for artifacts");
            List<CompletableFuture<Void>> futures = artifacts
                    .stream()
                    .map(artifact -> CompletableFuture.runAsync(
                            () -> prepareArtifactJavadocBundle(sitePath, artifact),
                            executor
                    ))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("Completed preparing javadoc site bundles for artifacts");
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to prepare javadoc site bundles for artifacts", ex);
        }
    }

    private void prepareArtifactJavadocBundle(@NonNull String sitePath, @NonNull MavenArtifact artifact) {
        try {
            log.info("Started preparing javadoc site bundle for artifact: {}", artifact);

            // Create directory for the artifact if not exists
            Path path = Path.of(sitePath, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
            Files.createDirectories(path);
            log.info("Created directory for artifact: {}", path);

            // Download the javadoc zip from Maven Central and extract it to the artifact directory using streaming
            @Cleanup ZipInputStream zipInputStream = new ZipInputStream(mavenCentralClient.getJavadocJarInputStream(artifact));
            ZipEntry entry;
            while((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = path.resolve(entry.getName()).normalize();
                if(entry.isDirectory()) Files.createDirectories(entryPath);
                else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zipInputStream, entryPath);
                }
                zipInputStream.closeEntry();
            }

            log.info("Completed preparing javadoc site bundle for artifact: {}", artifact);
        }
        catch (Exception ex) {
            throw new RuntimeException(MessageFormat.format(
                    "Failed to prepare javadoc site bundle for artifact: {0}", artifact
            ), ex);
        }
    }
}
