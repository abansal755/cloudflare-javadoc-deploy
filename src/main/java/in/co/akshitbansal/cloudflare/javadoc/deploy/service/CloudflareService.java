package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.annotation.Retry;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.CloudflareClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.Asset;
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
        validateAndRefreshUploadToken();
        return cloudflareClient.checkMissingHashes(hashes, uploadToken);
    }

    @Retry
    public void uploadAssetsBucket(List<BundleFile> bucket) {
        List<Asset> assets = bucket
                .stream()
                .map(file -> new Asset(file.getHash(), file.getBase64Content(), file.getContentType()))
                .toList();
        validateAndRefreshUploadToken();
        cloudflareClient.uploadAssets(assets, uploadToken);

        long bucketSizeInBytes = bucket
                .stream()
                .mapToLong(BundleFile::getSizeInBytes)
                .sum();
        log.info("Uploaded a bucket of files to Cloudflare. Bucket size in bytes: {}, File count: {}", bucketSizeInBytes, bucket.size());
    }

    @Retry
    public void upsertHashes(List<String> hashes) {
        validateAndRefreshUploadToken();
        cloudflareClient.upsertHashes(hashes, uploadToken);
    }

    @Retry
    public String triggerDeployment(Map<String, String> manifest) {
        validateAndRefreshUploadToken();
        return cloudflareClient.triggerDeployment(manifest);
    }

    @Retry
    public DeploymentStage getLatestDeploymentStage(String deploymentId) {
        validateAndRefreshUploadToken();
        return cloudflareClient.getLatestDeploymentStage(deploymentId);
    }

    private void validateAndRefreshUploadToken() {
        // If the token is valid, no need to refresh
        if(isUploadTokenValid()) return;
        synchronized (this) {
            // Double-check if the token is still invalid after acquiring the lock to avoid unnecessary refreshes
            if(isUploadTokenValid()) return;

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
