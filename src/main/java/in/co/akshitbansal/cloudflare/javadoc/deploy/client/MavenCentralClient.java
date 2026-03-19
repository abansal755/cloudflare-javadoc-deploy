package in.co.akshitbansal.cloudflare.javadoc.deploy.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.annotation.Retry;
import in.co.akshitbansal.cloudflare.javadoc.deploy.enums.DeploymentStatus;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.DeploymentStatusRes;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import jakarta.inject.Named;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import tools.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Singleton
@Slf4j
public class MavenCentralClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<MavenRepository> repositories;

    private final String BASE_URL;
    private final String GET_DEPLOYMENT_STATUS_ENDPOINT;
    private final String USERNAME;
    private final String PASSWORD;

    @Inject
    public MavenCentralClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            List<MavenRepository> repositories,
            @Named("maven-central.base-url") String BASE_URL,
            @Named("maven-central.get-deployment-status-endpoint") String GET_DEPLOYMENT_STATUS_ENDPOINT,
            @Named("maven-central.username") String USERNAME,
            @Named("maven-central.password") String PASSWORD
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.repositories = repositories;
        this.BASE_URL = BASE_URL;
        this.GET_DEPLOYMENT_STATUS_ENDPOINT = GET_DEPLOYMENT_STATUS_ENDPOINT;
        this.USERNAME = USERNAME;
        this.PASSWORD = PASSWORD;
    }

    @Retry
    public List<MavenArtifact> getArtifacts(@NonNull MavenPackage mavenPackage) {
        try {
            List<String> versions = new ArrayList<>();
            for(MavenRepository repository: repositories) {
                Document document = DocumentBuilderFactory
                        .newInstance()
                        .newDocumentBuilder()
                        .parse(repository.getBaseUrl() + getMetadataPath(mavenPackage));
                XPath xPath = XPathFactory
                        .newInstance()
                        .newXPath();
                NodeList nodes = (NodeList) xPath.evaluate("/metadata/versioning/versions/version", document, javax.xml.xpath.XPathConstants.NODESET);
                for(int idx = 0; idx < nodes.getLength(); idx++)
                    versions.add(nodes.item(idx).getTextContent());
            }
            log.info("Fetched versions for package {}: {}", mavenPackage, versions);
            return versions
                    .stream()
                    .map(version -> new MavenArtifact(mavenPackage.getGroupId(), mavenPackage.getArtifactId(), version))
                    .toList();
        }
        catch (IOException ex) {
            throw new RetryableException("Recoverable exception occurred while trying to fetch artifact versions for package: " + mavenPackage, ex);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to fetch versions for artifact: " + mavenPackage, ex);
        }
    }

    @Retry
    public InputStream getJavadocJarInputStream(@NonNull MavenArtifact mavenArtifact) {
        try {
            for(MavenRepository repository: repositories) {
                URI uri = getJavadocArtifactURI(repository, mavenArtifact);
                log.info("GET {}", uri);
                HttpRequest request = HttpRequest
                        .newBuilder()
                        .uri(uri)
                        .build();
                HttpResponse<InputStream> response;
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                }
                catch (IOException | SecurityException ex) {
                    throw new RetryableException(MessageFormat.format(
                            "Recoverable exception occurred while trying to fetch javadoc jar for artifact {0} from repository {1}",
                            mavenArtifact, repository
                    ), ex);
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    throw new RuntimeException(MessageFormat.format(
                            "Thread was interrupted while trying to fetch javadoc jar for artifact {0} from repository {1}",
                            mavenArtifact, repository
                    ), ex);
                }
                int statusCode = response.statusCode();
                InputStream bodyStream = response.body();
                if(statusCode >= 200 && statusCode < 300) {
                    log.info("Found javadoc jar for artifact {} in repository {}", mavenArtifact, repository);
                    return bodyStream; // caller is responsible for closing the stream after consuming it
                }
                if(statusCode == 404) {
                    log.warn("Javadoc jar for artifact {} not found in repository {}", mavenArtifact, repository);
                    bodyStream.close();
                    continue;
                }
                String errorOutput = new String(bodyStream.readAllBytes());
                bodyStream.close();
                String message = MessageFormat.format(
                        "Failed to fetch javadoc jar for artifact {0} from repository {1}. HTTP status code: {2}. Error output: {3}",
                        mavenArtifact, repository, statusCode, errorOutput
                );
                if(statusCode == 429 || statusCode == 408 || statusCode >= 500) throw new RetryableException(message);
                throw new IllegalStateException(message);
            }
            throw new IllegalStateException(MessageFormat.format(
                    "Unable to locate javadoc jar for artifact {0}",
                    mavenArtifact
            ));
        }
        catch (RetryableException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to download artifact: " + mavenArtifact, ex);
        }
    }

    @Retry
    public DeploymentStatus getDeploymentStatus(@NonNull String deploymentId) {
        try {
            URI uri = URI.create(BASE_URL + GET_DEPLOYMENT_STATUS_ENDPOINT + "?id=" + deploymentId);
            log.info("POST {}", uri);
            HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(uri)
                    .header("Authorization", "Bearer " + getBearerToken(USERNAME, PASSWORD))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response;
            try {
                response= httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }
            catch (IOException ex) {
                throw new RetryableException("Recoverable exception occurred while trying to fetch deployment status for deployment ID: " + deploymentId, ex);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                throw new RuntimeException("Thread was interrupted while trying to fetch deployment status for deployment ID: " + deploymentId, ex);
            }
            int statusCode = response.statusCode();
            if(statusCode == 429 || statusCode == 408 || statusCode >= 500) {
                throw new RetryableException(MessageFormat.format(
                        "Received HTTP status code {0} while trying to fetch deployment status for deployment ID: {1}",
                        statusCode, deploymentId
                ));
            }
            if(statusCode >= 400) {
                throw new IllegalStateException(MessageFormat.format(
                        "Failed to fetch deployment status for deployment ID: {0}. HTTP status code: {1}",
                        deploymentId, statusCode
                ));
            }
            DeploymentStatusRes deploymentStatusRes = objectMapper.readValue(response.body(), DeploymentStatusRes.class);
            DeploymentStatus status = deploymentStatusRes.getDeploymentState();
            log.info("Fetched deployment status for deployment ID {}: {}", deploymentId, status);
            return status;
        }
        catch (RetryableException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to fetch deployment status for deployment ID: " + deploymentId, ex);
        }
    }

    private String getMetadataPath(MavenPackage mavenPackage) {
        return String.format(
                "/%s/%s/maven-metadata.xml",
                mavenPackage.getGroupId().replace(".", "/"),
                mavenPackage.getArtifactId()
        );
    }

    private URI getJavadocArtifactURI(MavenRepository repository, MavenArtifact mavenArtifact) {
        if(!repository.isSnapshotRepository()) return URI.create(repository.getBaseUrl() + getJavadocPath(mavenArtifact));
        return URI.create(repository.getBaseUrl() + getSnapshotJavadocPath(mavenArtifact, repository));
    }

    private String getJavadocPath(MavenArtifact mavenArtifact) {
        return String.format(
                "/%s/%s/%s/%s-%s-javadoc.jar",
                mavenArtifact.getGroupId().replace(".", "/"),
                mavenArtifact.getArtifactId(),
                mavenArtifact.getVersion(),
                mavenArtifact.getArtifactId(),
                mavenArtifact.getVersion()
        );
    }

    private String getSnapshotJavadocPath(@NonNull MavenArtifact mavenArtifact, @NonNull MavenRepository repository) {
        try {
            Document document = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(repository.getBaseUrl() + getSnapshotMetadataPath(mavenArtifact));
            XPath xPath = XPathFactory
                    .newInstance()
                    .newXPath();
            String value = xPath.evaluate("/metadata/versioning/snapshotVersions/snapshotVersion[classifier='javadoc' and extension='jar']/value", document);
            return String.format("/%s/%s/%s/%s-%s-javadoc.jar",
                    mavenArtifact.getGroupId().replace(".", "/"),
                    mavenArtifact.getArtifactId(),
                    mavenArtifact.getVersion(),
                    mavenArtifact.getArtifactId(),
                    value
            );
        }
        catch (IOException ex) {
            throw new RetryableException("Recoverable exception occurred while trying to fetch snapshot metadata for artifact: " + mavenArtifact, ex);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to fetch javadoc snapshot jar path for artifact: " + mavenArtifact, ex);
        }
    }

    private String getSnapshotMetadataPath(MavenArtifact mavenArtifact) {
        return String.format(
                "/%s/%s/%s/maven-metadata.xml",
                mavenArtifact.getGroupId().replace(".", "/"),
                mavenArtifact.getArtifactId(),
                mavenArtifact.getVersion()
        );
    }

    private String getBearerToken(@NonNull String username, @NonNull String password) {
        Base64.Encoder encoder = Base64.getEncoder();
        String val = username + ":" + password;
        return encoder.encodeToString(val.getBytes());
    }
}
