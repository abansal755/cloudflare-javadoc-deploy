package in.co.akshitbansal.client;

import in.co.akshitbansal.model.MavenArtifact;
import in.co.akshitbansal.model.MavenPackage;
import lombok.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MavenCentralClient {

    private static final String BASE_URL = "https://repo1.maven.org/maven2";

    public static List<MavenArtifact> getArtifacts(@NonNull MavenPackage mavenPackage) {
        try {
            Document document = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(BASE_URL + getMetadataPath(mavenPackage));
            XPath xPath = XPathFactory
                    .newInstance()
                    .newXPath();
            NodeList nodes = (NodeList) xPath.evaluate("/metadata/versioning/versions/version", document, javax.xml.xpath.XPathConstants.NODESET);
            List<MavenArtifact> artifacts = new ArrayList<>();
            for(int idx = 0; idx < nodes.getLength(); idx++)
                artifacts.add(new MavenArtifact(
                        mavenPackage.getGroupId(),
                        mavenPackage.getArtifactId(),
                        nodes.item(idx).getTextContent()
                ));
            return artifacts;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch versions for artifact: " + mavenPackage, ex);
        }
    }

    public static void downloadArtifactToFilesystem(@NonNull MavenArtifact mavenArtifact, @NonNull String destinationPath) throws IOException {
        HttpClient client = null;
        BufferedInputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        try {
            client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create(BASE_URL + getJavadocPath(mavenArtifact)))
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            inputStream = new BufferedInputStream(response.body());
            outputStream = new BufferedOutputStream(Files.newOutputStream(Path.of(destinationPath)));
            int b = -1;
            while((b = inputStream.read()) != -1)
                outputStream.write(b);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to download artifact: " + mavenArtifact, ex);
        }
        finally {
            if(inputStream != null) inputStream.close();
            if(outputStream != null) {
                outputStream.flush();
                outputStream.close();
            }
            if(client != null) client.close();
        }
    }

    private static String getMetadataPath(MavenPackage mavenPackage) {
        return String.format(
                "/%s/%s/maven-metadata.xml",
                mavenPackage.getGroupId().replace(".", "/"),
                mavenPackage.getArtifactId()
        );
    }

    private static String getJavadocPath(MavenArtifact mavenArtifact) {
        return String.format(
                "/%s/%s/%s/%s-%s-javadoc.jar",
                mavenArtifact.getGroupId().replace(".", "/"),
                mavenArtifact.getArtifactId(),
                mavenArtifact.getVersion(),
                mavenArtifact.getArtifactId(),
                mavenArtifact.getVersion()
        );
    }
}
