package in.co.akshitbansal.cloudflare.javadoc.deploy.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import in.co.akshitbansal.cloudflare.javadoc.deploy.FailFastHttpClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.*;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

@Singleton
public class CloudflareClient {

    private final FailFastHttpClient httpClient;
    private final CloseableHttpClient apacheHttpClient;
    private final ObjectMapper objectMapper;

    private final String CLOUDFLARE_ACCOUNT_ID;
    private final String CLOUDFLARE_API_TOKEN;
    private final String CLOUDFLARE_PROJECT_NAME;

    private final String CLOUDFLARE_BASE_URL;
    private final String CLOUDFLARE_GET_UPLOAD_TOKEN_ENDPOINT;
    private final String CLOUDFLARE_CHECK_MISSING_HASHES_ENDPOINT;
    private final String CLOUDFLARE_UPLOAD_ASSETS_ENDPOINT;
    private final String CLOUDFLARE_UPSERT_HASHES_ENDPOINT;
    private final String CLOUDFLARE_TRIGGER_DEPLOYMENT_ENDPOINT;
    private final String CLOUDFLARE_GET_DEPLOYMENT_STATUS_ENDPOINT;

    @Inject
    public CloudflareClient(
            FailFastHttpClient httpClient,
            CloseableHttpClient apacheHttpClient,
            ObjectMapper objectMapper,
            Config config
    ) {
        this.httpClient = httpClient;
        this.apacheHttpClient = apacheHttpClient;
        this.objectMapper = objectMapper;
        this.CLOUDFLARE_ACCOUNT_ID = config.getString("stage.cloudflare-deployment.account-id");
        this.CLOUDFLARE_API_TOKEN = config.getString("stage.cloudflare-deployment.api-token");
        this.CLOUDFLARE_PROJECT_NAME = config.getString("stage.cloudflare-deployment.project-name");
        this.CLOUDFLARE_BASE_URL = config.getString("stage.cloudflare-deployment.base-url");
        this.CLOUDFLARE_GET_UPLOAD_TOKEN_ENDPOINT = config.getString("stage.cloudflare-deployment.get-upload-token.endpoint");
        this.CLOUDFLARE_CHECK_MISSING_HASHES_ENDPOINT = config.getString("stage.cloudflare-deployment.check-missing-hashes.endpoint");
        this.CLOUDFLARE_UPLOAD_ASSETS_ENDPOINT = config.getString("stage.cloudflare-deployment.upload-assets.endpoint");
        this.CLOUDFLARE_UPSERT_HASHES_ENDPOINT = config.getString("stage.cloudflare-deployment.upsert-hashes.endpoint");
        this.CLOUDFLARE_TRIGGER_DEPLOYMENT_ENDPOINT = config.getString("stage.cloudflare-deployment.trigger-deployment.endpoint");
        this.CLOUDFLARE_GET_DEPLOYMENT_STATUS_ENDPOINT = config.getString("stage.cloudflare-deployment.get-deployment-status.endpoint");
    }

    public String getUploadToken() {
        String path = MessageFormat.format(
                CLOUDFLARE_BASE_URL + CLOUDFLARE_GET_UPLOAD_TOKEN_ENDPOINT,
                CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_PROJECT_NAME
        );
        HttpRequest request = HttpRequest
                .newBuilder()
                .GET()
                .uri(URI.create(path))
                .header("Authorization", "Bearer " + CLOUDFLARE_API_TOKEN)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        GetUploadTokenRes tokenRes = objectMapper.readValue(response.body(), GetUploadTokenRes.class);
        return tokenRes.getResult().getJwt();
    }

    public List<String> checkMissingHashes(List<String> hashes, String uploadToken) {
        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(URI.create(CLOUDFLARE_BASE_URL + CLOUDFLARE_CHECK_MISSING_HASHES_ENDPOINT))
                .header("Authorization", "Bearer " + uploadToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(new HashesReq(hashes))))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        CheckMissingHashesRes missingHashesRes = objectMapper.readValue(response.body(), CheckMissingHashesRes.class);
        return missingHashesRes.getResult();
    }

    public void uploadAssets(List<Asset> assets, String uploadToken) {
        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(URI.create(CLOUDFLARE_BASE_URL + CLOUDFLARE_UPLOAD_ASSETS_ENDPOINT))
                .header("Authorization", "Bearer " + uploadToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(assets)))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    public void upsertHashes(List<String> hashes, String uploadToken) {
        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(URI.create(CLOUDFLARE_BASE_URL + CLOUDFLARE_UPSERT_HASHES_ENDPOINT))
                .header("Authorization", "Bearer " + uploadToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(new HashesReq(hashes))))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    public String triggerDeployment(Map<String, String> manifest) {
        try {
            String path = MessageFormat.format(
                    CLOUDFLARE_BASE_URL + CLOUDFLARE_TRIGGER_DEPLOYMENT_ENDPOINT,
                    CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_PROJECT_NAME
            );
            String response = Request
                    .post(path)
                    .setHeader("Authorization", "Bearer " + CLOUDFLARE_API_TOKEN)
                    .body(MultipartEntityBuilder
                            .create()
                            .addTextBody("manifest", objectMapper.writeValueAsString(manifest))
                            .build()
                    )
                    .execute(apacheHttpClient)
                    .returnContent()
                    .asString();

            JsonNode root = objectMapper.readTree(response);
            JsonNode idNode = root
                    .path("result")
                    .path("id");
            if(idNode.getNodeType() != JsonNodeType.STRING)
                throw new IllegalStateException("Deployment ID not found in response at path 'result.id'. Full response: " + response);
            return idNode.stringValue();
        }
        catch (IOException ex) {
            throw new RetryableException("Retryable IOException occurred while triggering deployment", ex);
        }
    }

    public DeploymentStage getLatestDeploymentStage(String deploymentId) {
        String path = MessageFormat.format(
                CLOUDFLARE_BASE_URL + CLOUDFLARE_GET_DEPLOYMENT_STATUS_ENDPOINT,
                CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_PROJECT_NAME, deploymentId
        );
        HttpRequest request = HttpRequest
                .newBuilder()
                .GET()
                .uri(URI.create(path))
                .header("Authorization", "Bearer " + CLOUDFLARE_API_TOKEN)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode stagNode = root
                .path("result")
                .path("latest_stage");
        return objectMapper.treeToValue(stagNode, DeploymentStage.class);
    }
}
