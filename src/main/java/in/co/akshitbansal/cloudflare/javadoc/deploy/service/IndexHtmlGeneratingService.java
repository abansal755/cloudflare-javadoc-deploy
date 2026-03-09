package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.VersionComparator;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
public class IndexHtmlGeneratingService {

    private final ExecutorService executor;
    private final Template freemarkerTemplate;

    public void generateIndexHtml(String startingPath, int maxDepth) {
        generateIndexHtml(startingPath, "/", maxDepth);
    }

    private void generateIndexHtml(String currentPath, String relativePath, int depth) {
        if(depth <= 0) return; // Base case: stop recursion when depth limit is reached
        try {
            log.info("Started recursively generating index.html for subdirectories of path: {}", currentPath);

            // List directories in the current path
            Path path = Path.of(currentPath);
            List<String> dirs = getDirectories(path);
            log.info("Found directories: {}", dirs);

            // Generate index.html using the template and save it to the current directory
            generateAndSaveIndexHtml(path, relativePath, dirs);

            // Recursively generate index.html for each subdirectory in parallel
            List<CompletableFuture<Void>> futures = dirs
                    .stream()
                    .map(dir -> CompletableFuture.runAsync(
                            () -> generateIndexHtml(
                                    path.resolve(dir).toString(),
                                    relativePath + dir + "/",
                                    depth - 1
                            ),
                            executor
                    ))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.info("Completed recursively generating index.html for subdirectories of path: {}", currentPath);
        }
        catch (Exception ex) {
            throw new RuntimeException(MessageFormat.format(
                    "Failed to recursively generate index.html for path: {0}", currentPath
            ), ex);
        }
    }

    private List<String> getDirectories(Path path) throws IOException {
        @Cleanup Stream<Path> stream = Files.list(path);
        return stream
                .filter(Files::isDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .sorted(new VersionComparator())
                .toList();
    }

    private void generateAndSaveIndexHtml(Path path, String relativePath, List<String> dirs) throws IOException, TemplateException {
        @Cleanup BufferedWriter writer = Files.newBufferedWriter(path.resolve("index.html"));
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("currentPath", relativePath);
        dataModel.put("directories", dirs);
        if (!relativePath.equals("/")) dataModel.put("parentPath", "..");
        freemarkerTemplate.process(dataModel, writer);
        log.info("Generated index.html for path: {}", path);
    }
}
