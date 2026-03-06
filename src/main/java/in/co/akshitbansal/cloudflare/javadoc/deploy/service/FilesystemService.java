package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
public class FilesystemService {

    public void deleteDirectoryRecursively(String path) {
        log.info("Started deleting directory recursively at path: {}", path);
        try {
            Files.walkFileTree(Path.of(path), new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    log.info("Deleted file at path: {}", file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    log.info("Deleted directory at path: {}", dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Completed deleting directory recursively at path: {}", path);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to delete directory recursively at path: " + path, ex);
        }
    }
}
