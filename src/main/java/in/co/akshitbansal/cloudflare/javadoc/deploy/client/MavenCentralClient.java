package in.co.akshitbansal.cloudflare.javadoc.deploy.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import in.co.akshitbansal.cloudflare.javadoc.deploy.annotation.Retry;
import in.co.akshitbansal.cloudflare.javadoc.deploy.enums.DeploymentStatus;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.HttpStatusException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableHttpStatusException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.DeploymentStatusRes;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.net.URIBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import tools.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Singleton
@Slf4j
public class MavenCentralClient {

    private final CloseableHttpClient apacheHttpClient;
    private final ObjectMapper objectMapper;
    private final List<MavenRepository> repositories;

    private final String BASE_URL;
    private final String GET_DEPLOYMENT_STATUS_ENDPOINT;
    private final String USERNAME;
    private final String PASSWORD;

    @Inject
    public MavenCentralClient(
            CloseableHttpClient apacheHttpClient,
            ObjectMapper objectMapper,
            List<MavenRepository> repositories,
            Config config
    ) {
        this.apacheHttpClient = apacheHttpClient;
        this.objectMapper = objectMapper;
        this.repositories = repositories;
        this.BASE_URL = config.getString("maven-central.base-url");
        this.GET_DEPLOYMENT_STATUS_ENDPOINT = config.getString("maven-central.get-deployment-status-endpoint");
        this.USERNAME = config.getString("maven-central.username");
        this.PASSWORD = config.getString("maven-central.password");
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
                try {
                    InputStream response = Request
                            .get(getJavadocArtifactURI(repository, mavenArtifact))
                            .execute(apacheHttpClient)
                            .returnContent()
                            .asStream();
                    log.info("Found javadoc jar for artifact {} in repository {}", mavenArtifact, repository);
                    return response; // caller is responsible for closing the stream after consuming it
                }
                catch (IOException ex) {
                    throw new RetryableException(MessageFormat.format(
                            "Retryable IOException occurred while trying to fetch javadoc jar for artifact {0} from repository {1}",
                            mavenArtifact, repository
                    ), ex);
                }
                catch (HttpStatusException ex) {
                    ClassicHttpResponse response = ex.getResponse();
                    int statusCode = response.getCode();
                    if(statusCode == 404) {
                        // Not throwing exception here because not all repositories may have the javadoc jar for the artifact, we want to try all repositories before giving up
                        log.warn("Javadoc jar for artifact {} not found in repository {}", mavenArtifact, repository);
                        continue;
                    }
                    throw ex;
                }
            }
            throw new IllegalStateException(MessageFormat.format(
                    "Unable to locate javadoc jar for artifact {0}",
                    mavenArtifact
            ));
        }
        catch (RetryableException | RetryableHttpStatusException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to download artifact: " + mavenArtifact, ex);
        }
    }

    @Retry
    public DeploymentStatus getDeploymentStatus(@NonNull String deploymentId) {
        try {
            URI uri = new URIBuilder(BASE_URL + GET_DEPLOYMENT_STATUS_ENDPOINT)
                    .addParameter("id", deploymentId)
                    .build();
            String response = Request
                    .post(uri)
                    .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + getBearerToken(USERNAME, PASSWORD))
                    .execute(apacheHttpClient)
                    .returnContent()
                    .asString();

            DeploymentStatusRes deploymentStatusRes = objectMapper.readValue(response, DeploymentStatusRes.class);
            DeploymentStatus status = deploymentStatusRes.getDeploymentState();
            log.info("Fetched deployment status for deployment ID {}: {}", deploymentId, status);
            return status;
        }
        catch (IOException ex) {
            throw new RetryableException("Retryable IOException occurred while fetching deployment status for deployment ID: " + deploymentId, ex);
        }
        catch (RetryableException | RetryableHttpStatusException ex) {
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
