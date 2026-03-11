package in.co.akshitbansal.cloudflare.javadoc.deploy.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.annotation.Retry;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

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
import java.util.List;

@Singleton
@Slf4j
public class MavenCentralClient {

    private final HttpClient httpClient;
    private final List<MavenRepository> repositories;

    @Inject
    public MavenCentralClient(HttpClient httpClient, List<MavenRepository> repositories) {
        this.httpClient = httpClient;
        this.repositories = repositories;
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
}
