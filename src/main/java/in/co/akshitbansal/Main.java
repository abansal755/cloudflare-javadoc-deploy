package in.co.akshitbansal;

import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.client.MavenCentralClient;
import in.co.akshitbansal.model.MavenArtifact;
import in.co.akshitbansal.model.MavenPackage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class Main {

    public static void main(String[] args) throws IOException {
        try {
            log.info("Started cloudflare javadoc deployment");
            // List of artifacts from system property
            String packagesArg = System.getProperty("packages");
            if(packagesArg == null)
                throw new IllegalArgumentException("No artifacts provided. Please provide a comma separated list of artifacts in the format groupId:artifactId using the 'packages' system property");

            // cloudflare pages project name and api token from system properties
            String projectName = System.getProperty("cloudflare.project-name");
            String apiToken = System.getProperty("cloudflare.api-token");
            if(projectName == null)
                throw new IllegalArgumentException("No Cloudflare Pages project name provided. Please provide the project name using the 'cloudflare.project-name' system property");
            if(apiToken == null)
                throw new IllegalArgumentException("No Cloudflare API token provided. Please provide the API token using the 'cloudflare.api-token' system property");

            List<MavenPackage> packages = Arrays
                    .stream(packagesArg.split(","))
                    .map(artifact -> {
                        String[] splits = artifact.split(":");
                        return new MavenPackage(splits[0], splits[1]);
                    })
                    .toList();
            log.info("Processing the following artifacts: {}", packages);

            // Create a temporary directory to manage the javadoc site bundles before moving them to the final location
            Path tempDir = Files.createTempDirectory("cloudflare-javadoc");
            String sitePath = tempDir.toString();
            log.info("Created temporary directory for javadoc site bundle: {}", sitePath);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                // Fetch all versions for each artifact in parallel
                List<MavenArtifact> artifacts = getAllArtifacts(packages, executor);

                // Prepare javadoc site bundle for each artifact in parallel
                log.info("Started preparing javadoc site bundles for all artifacts");
                List<CompletableFuture<Void>> futures1 = artifacts
                        .stream()
                        .map(artifact -> CompletableFuture.runAsync(
                                () -> prepareJavadocSiteBundle(sitePath, artifact),
                                executor
                        ))
                        .toList();
                CompletableFuture.allOf(futures1.toArray(new CompletableFuture[0])).join();
                log.info("Completed preparing javadoc site bundles for all artifacts");

                // Generate index.html files
                log.info("Started generating index.html files");
                Configuration config = new Configuration(Configuration.VERSION_2_3_34);
                config.setClassLoaderForTemplateLoading(Main.class.getClassLoader(), "");
                config.setDefaultEncoding("UTF-8");
                Template template = config.getTemplate("package-index.ftl");
                generateIndexHtml(sitePath, "/", 2, template, executor);
                log.info("Completed generating index.html files");

                // Deploy the javadoc site to Cloudflare Pages using Wrangler CLI
                deployToCloudflare(sitePath, projectName, apiToken);
            }
            log.info("Completed cloudflare javadoc deployment");
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed cloudflare javadoc deployment", ex);
        }
    }

    private static List<MavenArtifact> getAllArtifacts(@NonNull List<MavenPackage> packages, @NonNull ExecutorService executor) {
        log.info("Started fetching artifact versions from Maven Central");
        List<CompletableFuture<List<MavenArtifact>>> futures = packages
                .stream()
                .map(artifact -> CompletableFuture.supplyAsync(() -> MavenCentralClient
                        .getArtifacts(artifact)
                        .stream()
                        .toList(), executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Completed fetching artifact versions from Maven Central");
        return futures
                .stream()
                .flatMap(future -> future.join().stream())
                .toList();
    }

    private static void prepareJavadocSiteBundle(@NonNull String sitePath, @NonNull MavenArtifact artifact) {
        try {
            log.info("Started preparing javadoc site bundle for artifact: {}", artifact);
            // Create directory for the artifact if not exists
            Path path = Path.of(sitePath, artifact.getArtifactId(), artifact.getVersion());
            Files.createDirectories(path);
            log.info("Created directory for artifact: {}", path);

            // Download the javadoc zip from Maven Central and extract it to the artifact directory using streaming
            try(ZipInputStream zipInputStream = new ZipInputStream(MavenCentralClient.getJavadocInputStream(artifact))) {
                ZipEntry entry = null;
                while((entry = zipInputStream.getNextEntry()) != null) {
                    Path entryPath = path.resolve(entry.getName()).normalize();
                    if(entry.isDirectory()) Files.createDirectories(entryPath);
                    else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zipInputStream, entryPath);
                    }
                    zipInputStream.closeEntry();
                }
            }
            log.info("Completed preparing javadoc site bundle for artifact: {}", artifact);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to prepare javadoc site bundle for artifact: " + artifact, ex);
        }
    }

    private static void generateIndexHtml(String currentPath, String relativePath, int depth, Template template, ExecutorService executor) {
        if(depth == 0) return;
        try {
            log.info("Started generating index.html for path: {}", currentPath);

            // List directories in the current path
            Path path = Path.of(currentPath);
            List<String> dirs;
            try(Stream<Path> stream = Files.list(path)) {
                dirs = stream
                        .filter(Files::isDirectory)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .sorted()
                        .toList();
            }
            log.info("Found directories for path {}: {}", currentPath, dirs);

            // Generate index.html using the template and save it to the current directory
            try (BufferedWriter writer = Files.newBufferedWriter(path.resolve("index.html"))) {
                Map<String, Object> dataModel = new HashMap<>();
                dataModel.put("currentPath", relativePath);
                dataModel.put("directories", dirs);
                if (!relativePath.equals("/")) dataModel.put("parentPath", "..");
                template.process(dataModel, writer);
            }
            log.info("Generated index.html for path: {}", currentPath);


            // Recursively generate index.html for each subdirectory in parallel
            log.info("Started generating index.html for subdirectories of path: {}", currentPath);
            List<CompletableFuture<Void>> futures = dirs
                    .stream()
                    .map(dir -> CompletableFuture.runAsync(
                            () -> generateIndexHtml(
                                    path.resolve(dir).toString(),
                                    relativePath + dir + "/",
                                    depth - 1,
                                    template,
                                    executor
                            ),
                            executor
                    ))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("Completed generating index.html for subdirectories of path: {}", currentPath);

            log.info("Completed generating index.html for path: {}", currentPath);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to generate index.html for path: " + currentPath, ex);
        }
    }

    private static void deleteDirectoryRecursively(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to recursively delete directory: " + path, ex);
        }
    }

    private static void deployToCloudflare(String sitePath, String projectName, String apiToken) {
        try {
            log.info("Started deploying javadoc site to Cloudflare Pages using Wrangler CLI");
            ProcessBuilder builder = new ProcessBuilder(
                    "npx",
                    "wrangler",
                    "pages",
                    "deploy",
                    sitePath,
                    "--project-name=" + projectName
            );
            Map<String,String> env = builder.environment();
            env.put("CLOUDFLARE_API_TOKEN", apiToken);

            Process process = builder.start();
            int exitCode = process.waitFor();
            if(exitCode != 0) {
                String errorOutput = new String(process.getErrorStream().readAllBytes());
                throw new RuntimeException("Failed to deploy javadoc site to Cloudflare Pages. Wrangler exited with code " + exitCode + ". Error output: " + errorOutput);
            }
            String output = new String(process.getInputStream().readAllBytes());
            log.info("Successfully deployed javadoc site to Cloudflare Pages. Wrangler output: {}", output);
        }
        catch (IOException | InterruptedException ex) {
            throw new RuntimeException("Failed to deploy javadoc site to Cloudflare Pages", ex);
        }
    }
}