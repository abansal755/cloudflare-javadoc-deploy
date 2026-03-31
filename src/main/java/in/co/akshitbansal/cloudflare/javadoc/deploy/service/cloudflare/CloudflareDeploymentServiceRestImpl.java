package in.co.akshitbansal.cloudflare.javadoc.deploy.service.cloudflare;

import com.typesafe.config.Config;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.BundleFile;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.DeploymentStage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.CloudflareService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.FilesystemService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
public class CloudflareDeploymentServiceRestImpl implements CloudflareDeploymentService {

    private final CloudflareService cloudflareService;
    private final FilesystemService filesystemService;
    private final ExecutorService executor;

    private final long MAX_BUCKET_SIZE_IN_BYTES; // 40 MB
    private final int MAX_BUCKET_FILE_COUNT;
    private final int MAX_POLLING_ATTEMPTS;
    private final long POLLING_INTERVAL_IN_SECONDS;

    public CloudflareDeploymentServiceRestImpl(
            CloudflareService cloudflareService,
            FilesystemService filesystemService,
            ExecutorService executor,
            Config config
    ) {
        this.cloudflareService = cloudflareService;
        this.filesystemService = filesystemService;
        this.executor = executor;

        MAX_BUCKET_SIZE_IN_BYTES = config.getLong("stage.cloudflare-deployment.upload-assets.max-bucket-size-in-bytes");
        MAX_BUCKET_FILE_COUNT = config.getInt("stage.cloudflare-deployment.upload-assets.max-bucket-file-count");
        MAX_POLLING_ATTEMPTS = config.getInt("stage.cloudflare-deployment.get-deployment-status.max-polling-attempts");
        POLLING_INTERVAL_IN_SECONDS = config.getLong("stage.cloudflare-deployment.get-deployment-status.polling-interval-in-seconds");
    }

    @Override
    public void deploy(Path sitePath) {
        try {
            log.info("Started deploying {} to Cloudflare Pages project using REST API implementation", sitePath);

            // List all files in the sitePath directory recursively and create BundleFile objects for each file
            List<BundleFile> files = filesystemService.listFilesRecursively(sitePath);
            log.info("Listed {} files in directory: {}", files.size(), sitePath);

            // For each BundleFile, compute the hash and base64 content in parallel
            populateBundleFiles(files, sitePath);

            // Create a map of hash to BundleFile for quick lookup
            Map<String, BundleFile> hashToFileMap = files
                    .stream()
                    .collect(Collectors.toMap(
                            BundleFile::getHash,
                            file -> file,
                            (existing, replacement) -> existing,
                            HashMap::new)
                    );

            // Check which files are missing in Cloudflare Pages
            List<String> hashes = files
                    .stream()
                    .map(BundleFile::getHash)
                    .toList();
            List<String> missingHashes = cloudflareService.checkMissingHashes(hashes);
            log.info("Checked missing hashes with Cloudflare. Missing files count: {}", missingHashes.size());
            // Get the list of missing files using the hashToFileMap
            List<BundleFile> missingFiles = missingHashes
                    .stream()
                    .map(hashToFileMap::get)
                    .toList();

            // Upload the missing files to Cloudflare Pages
            uploadMissingFiles(missingFiles);

            // Upsert all the hashes
            cloudflareService.upsertHashes(hashes);
            log.info("Upserted all hashes with Cloudflare. Total hashes upserted: {}", hashes.size());

            // Trigger deployment
            // Create a manifest of relative path to hash for all files to trigger deployment
            Map<String, String> manifest = files
                    .stream()
                    .collect(Collectors.toMap(
                            BundleFile::getRelativePath,
                            BundleFile::getHash,
                            (existing, replacement) -> existing,
                            HashMap::new
                    ));
            String deploymentId = cloudflareService.triggerDeployment(manifest);
            waitForDeploymentCompletion(deploymentId);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to deploy site to Cloudflare Pages project using REST API implementation for site path: " + sitePath, ex);
        }
    }

    private void populateBundleFiles(List<BundleFile> files, Path sitePath) {
        List<CompletableFuture<Void>> futures = files
                .stream()
                .map(file -> CompletableFuture.runAsync(() -> filesystemService.populateBundleFile(file), executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Computed hash and base64 content for all files in directory: {}", sitePath);
    }

    private void uploadMissingFiles(List<BundleFile> missingFiles) {
        List<List<BundleFile>> buckets = createBuckets(missingFiles);
        log.info("Created {} buckets for missing files to upload to Cloudflare", buckets.size());

        List<CompletableFuture<Void>> uploadFutures = buckets
                .stream()
                .map(bucket -> CompletableFuture.runAsync(() -> cloudflareService.uploadAssetsBucket(bucket), executor))
                .toList();
        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
        log.info("Uploaded all missing files to Cloudflare. Missing files count: {}", missingFiles.size());
    }

    private List<List<BundleFile>> createBuckets(List<BundleFile> files) {
        // Sort the files by size in descending order to optimize bucket filling
        files = files
                .stream()
                .sorted()
                .toList();

        List<List<BundleFile>> buckets = new ArrayList<>();
        List<BundleFile> currentBucket = new ArrayList<>();

        long currentBucketSize = 0;
        int currentBucketFileCount = 0;
        for(int idx = 0; idx < files.size(); idx++) {
            BundleFile file = files.get(idx);
            if(currentBucketSize + file.getSizeInBytes() > MAX_BUCKET_SIZE_IN_BYTES || currentBucketFileCount + 1 > MAX_BUCKET_FILE_COUNT) {
                buckets.add(currentBucket);
                currentBucket = new ArrayList<>();
                currentBucketSize = 0;
                currentBucketFileCount = 0;
            }
            currentBucket.add(file);
            currentBucketSize += file.getSizeInBytes();
            currentBucketFileCount++;
        }
        // Add the last bucket if it's not empty
        if(!currentBucket.isEmpty())
            buckets.add(currentBucket);
        return buckets;
    }

    private void waitForDeploymentCompletion(@NonNull String deploymentId) {
        try {
            // Poll for deployment status until it's successful or failed
            int attempts = 0;
            while(attempts < MAX_POLLING_ATTEMPTS) {
                DeploymentStage stage = cloudflareService.getLatestDeploymentStage(deploymentId);
                String stageName = stage.getName();
                String status = stage.getStatus();
                if(stageName.equals("deploy")) {
                    if(status.equals("success")) {
                        log.info("Deployment successful for deployment ID: {}. Deployment stage: {}, status: {}", deploymentId, stageName, status);
                        return;
                    }
                    else if(status.equals("failed")) {
                        throw new RuntimeException("Deployment failed for deployment ID: " + deploymentId + ". Deployment stage: " + stageName + ", status: " + status);
                    }
                }
                log.info("Deployment still in progress for deployment ID: {}. Current stage: {}, status: {}", deploymentId, stage.getName(), stage.getStatus());
                Thread.sleep(POLLING_INTERVAL_IN_SECONDS * 1000); // Sleep for 1 second before polling again
                attempts++;
            }
            log.warn("Deployment did not complete within the expected time for deployment ID: {}. Please check Cloudflare dashboard for more details.", deploymentId);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new RuntimeException("Thread was interrupted while waiting for deployment completion for deployment ID: " + deploymentId, ex);
        }
    }
}
