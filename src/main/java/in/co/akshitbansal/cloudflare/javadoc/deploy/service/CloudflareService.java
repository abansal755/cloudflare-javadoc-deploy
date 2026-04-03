package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.annotation.Retry;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.CloudflareClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.BundleFile;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.DeploymentStage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.UploadTokenJwtPayload;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Singleton
@Slf4j
public class CloudflareService {

    private final CloudflareClient cloudflareClient;
    private final ObjectMapper objectMapper;

    private final Base64.Decoder base64Decoder;

    private volatile String uploadToken;
    private volatile long uploadTokenExpireAt;

    @Inject
    public CloudflareService(CloudflareClient cloudflareClient, ObjectMapper objectMapper) {
        this.cloudflareClient = cloudflareClient;
        this.objectMapper = objectMapper;

        this.base64Decoder = Base64.getUrlDecoder();

        this.uploadToken = null;
    }

    @Retry
    public List<String> checkMissingHashes(List<String> hashes) {
        log.info("Checking missing hashes with Cloudflare for {} files", hashes.size());
        validateAndRefreshUploadToken();
        List<String> missingHashes = cloudflareClient.checkMissingHashes(hashes, uploadToken);
        log.info("Checked missing hashes with Cloudflare. Missing files count: {}", missingHashes.size());
        return missingHashes;
    }

    @Retry
    public void uploadBundleFiles(List<BundleFile> bundleFiles) {
        validateAndRefreshUploadToken();
        cloudflareClient.uploadBundleFiles(bundleFiles, uploadToken);
    }

    @Retry
    public void upsertHashes(List<String> hashes) {
        log.info("Upserting all hashes to Cloudflare. Total hashes to upsert: {}", hashes.size());
        validateAndRefreshUploadToken();
        cloudflareClient.upsertHashes(hashes, uploadToken);
        log.info("Completed upserting to hashes with Cloudflare");
    }

    @Retry
    public String triggerDeployment(Map<String, String> manifest) {
        log.info("Triggering deployment with Cloudflare");
        String deploymentId = cloudflareClient.triggerDeployment(manifest);
        log.info("Triggered deployment with Cloudflare. Deployment ID: {}", deploymentId);
        return deploymentId;
    }

    @Retry
    public DeploymentStage getLatestDeploymentStage(String deploymentId) {
        log.info("Fetching latest deployment stage from Cloudflare for deployment ID: {}", deploymentId);
        DeploymentStage stage = cloudflareClient.getLatestDeploymentStage(deploymentId);
        log.info("Fetched latest deployment stage from Cloudflare for deployment ID: {}. Stage: {}", deploymentId, stage);
        return stage;
    }

    private void validateAndRefreshUploadToken() {
        // If the token is valid, no need to refresh
        if(isUploadTokenValid()) return;
        synchronized (this) {
            // Double-check if the token is still invalid after acquiring the lock to avoid unnecessary refreshes
            if(isUploadTokenValid()) return;
            log.info("Upload token is missing or expired. Fetching a new upload token from Cloudflare");

            // Fetch a new upload token from Cloudflare
            this.uploadToken = cloudflareClient.getUploadToken();
            String[] parts = uploadToken.split("\\.");
            String b64Payload = parts[1];
            String payload = new String(base64Decoder.decode(b64Payload));
            UploadTokenJwtPayload parsed = objectMapper.readValue(payload, UploadTokenJwtPayload.class);
            this.uploadTokenExpireAt = parsed.getExpireAt() * 1000;
        }
    }

    private boolean isUploadTokenValid() {
        // if token is valid for at least the next 10 seconds, consider it valid to avoid edge cases during upload
        return uploadToken != null && System.currentTimeMillis() <= uploadTokenExpireAt - 10_000;
    }
}
