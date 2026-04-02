package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Singleton;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare.BundleFile;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.Blake3;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
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
            List<BundleFile> files = stream
                    .filter(Files::isRegularFile)
                    .map(filePath -> mapToBundleFile(filePath, path)) // Using the same path as basePath to get relative paths
                    .toList();
            log.info("Found {} files in directory: {}", files.size(), path);
            return files;
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to list files recursively at path: " + path, ex);
        }
    }

    public String getBundleFileBase64Content(@NonNull BundleFile bundleFile) {
        try {
            Path path = Path.of(bundleFile.getAbsolutePath());
            return base64Encoder.encodeToString(Files.readAllBytes(path));
        }
        catch (Exception ex) {
            throw new RuntimeException(MessageFormat.format(
                    "Exception occurred while reading file content for file: {0}",
                    bundleFile.getAbsolutePath()
            ));
        }
    }

    public void populateBundleFileMetadata(@NonNull BundleFile bundleFile) {
        try {
            Path path = Path.of(bundleFile.getAbsolutePath());
            bundleFile.setSizeInBytes(Files.size(path));
            bundleFile.setContentType(Files.probeContentType(path));
            populateBundleFileHash(bundleFile);
        }
        catch (Exception ex) {
            throw new RuntimeException(MessageFormat.format(
                    "Exception occurred while populating metadata for file: {0}",
                    bundleFile.getAbsolutePath()
            ));
        }
    }

    private void populateBundleFileHash(@NonNull BundleFile bundleFile) {
        Path path = Path.of(bundleFile.getAbsolutePath());
        try {
            Blake3OutputStream blakeOut = new Blake3OutputStream();
            try(InputStream in = Files.newInputStream(path);
                OutputStream base64Out = base64Encoder.wrap(blakeOut)) {

                byte[] buffer = new byte[8192];
                int read;
                while((read = in.read(buffer)) != -1) {
                    base64Out.write(buffer, 0, read);
                }
            }
            String extension = getFileExtension(path);
            blakeOut.write(extension.getBytes(StandardCharsets.UTF_8));
            byte[] digest = blakeOut.getDigest();
            String hash = Hex.encodeHexString(digest);
            bundleFile.setHash(hash);
        }
        catch (Exception ex) {
            throw new RuntimeException(MessageFormat.format(
                    "Exception occurred while populating hash for file: {0}", path
            ));
        }
    }

    private String getFileExtension(@NonNull Path path) {
        String fileName = path.getFileName().toString();
        int dotIdx = fileName.lastIndexOf('.');
        if(dotIdx != -1) return fileName.substring(dotIdx + 1);
        return "";
    }

    private BundleFile mapToBundleFile(Path filePath, Path basePath) {
        String relativePath = "/" + basePath.relativize(filePath).toString();
        return new BundleFile(filePath.toString(), relativePath);
    }

    private static class Blake3OutputStream extends OutputStream {

        private final Blake3 blake3;
        private final byte[] singleByte;

        public Blake3OutputStream() {
            this.blake3 = Blake3.initHash();
            singleByte = new byte[1];
        }

        @Override
        public void write(int b) throws IOException {
            singleByte[0] = (byte) b;
            blake3.update(singleByte);
        }

        @Override
        public void write(@NotNull byte[] b, int off, int len) throws IOException {
            blake3.update(b, off, len);
        }

        public byte[] getDigest() {
            return blake3.doFinalize(16);
        }
    }
}
