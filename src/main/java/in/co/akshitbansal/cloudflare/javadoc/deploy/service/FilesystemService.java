package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.BundleFile;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.Blake3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

@Singleton
@Slf4j
public class FilesystemService {

    private final Base64.Encoder base64Encoder;

    public FilesystemService() {
        this.base64Encoder = Base64.getEncoder();
    }

    public void deleteDirectoryRecursively(String path) {
        log.info("Started deleting directory recursively at path: {}", path);
        try {
            Files.walkFileTree(Path.of(path), new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    // log.info("Deleted file at path: {}", file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    // log.info("Deleted directory at path: {}", dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Completed deleting directory recursively at path: {}", path);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to delete directory recursively at path: " + path, ex);
        }
    }

    public List<BundleFile> listFilesRecursively(@NonNull Path path) {
        try {
            @Cleanup Stream<Path> stream = Files.walk(path);
            return stream
                    .filter(Files::isRegularFile)
                    .map(filePath -> mapToBundleFile(filePath, path)) // Using the same path as basePath to get relative paths
                    .toList();
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to list files recursively at path: " + path, ex);
        }
    }

    public void populateBundleFile(@NonNull BundleFile bundleFile) {
        Path path = Path.of(bundleFile.getAbsolutePath());
        try {
            // Read the file content as bytes
            byte[] bytes = Files.readAllBytes(path);
            bundleFile.setSizeInBytes(bytes.length); // Set the size in bytes
            // Encode the bytes to Base64 string
            String base64Content = base64Encoder.encodeToString(bytes);
            bundleFile.setBase64Content(base64Content);

            // Extract the file extension
            String fileName = path.getFileName().toString();
            int dotIdx = fileName.lastIndexOf('.');
            String extension = "";
            if(dotIdx != -1) extension = fileName.substring(dotIdx + 1);

            // Combine the Base64 content and the file extension, and compute the Blake3 hash
            byte[] input = (base64Content + extension).getBytes(StandardCharsets.UTF_8);
            byte[] output = Blake3.hash(input);
            String hash = Hex
                    .encodeHexString(output)
                    .substring(0, 32); // Truncate to 32 characters
            log.info("Computed hash for file at path: {}. Hash: {}", path, hash);
            bundleFile.setHash(hash);

            // Set the content type of the file
            bundleFile.setContentType(Files.probeContentType(path));
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to compute hash for file at path: " + path, ex);
        }
    }

    private BundleFile mapToBundleFile(Path filePath, Path basePath) {
        String relativePath = "/" + basePath.relativize(filePath).toString();
        return new BundleFile(filePath.toString(), relativePath);
    }
}
