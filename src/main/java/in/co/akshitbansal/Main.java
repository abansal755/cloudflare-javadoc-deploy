package in.co.akshitbansal;

import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.client.MavenCentralClient;
import in.co.akshitbansal.model.MavenArtifact;
import in.co.akshitbansal.model.MavenPackage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        // List of artifacts from 0th argument, separated by ","
        List<MavenPackage> packages = Arrays
                .stream(args[0].split(","))
                .map(artifact -> {
                    String[] splits = artifact.split(":");
                    return new MavenPackage(splits[0], splits[1]);
                })
                .toList();

        // Site directory from 1st argument
        String sitePath = args[1];

        ExecutorService executor = null;
        try {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            ExecutorService finalExecutor = executor;

            // Fetch all versions for each artifact in parallel
            List<CompletableFuture<List<MavenArtifact>>> futures = packages
                    .stream()
                    .map(artifact -> CompletableFuture.supplyAsync(() -> MavenCentralClient
                            .getArtifacts(artifact)
                            .stream()
                            .toList(), finalExecutor))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<MavenArtifact> artifacts = futures
                    .stream()
                    .flatMap(future -> future.join().stream())
                    .toList();

            // Download javadoc for each artifact in parallel and unzip contents
            List<CompletableFuture<Void>> futures1 = artifacts
                    .stream()
                    .map(artifact -> CompletableFuture.runAsync(() -> {
                        try {
                            Path path = Path.of(sitePath, artifact.getArtifactId(), artifact.getVersion());
                            Files.createDirectories(path);
                            Path jarPath = path.resolve("javadoc.jar");
                            MavenCentralClient.downloadArtifactToFilesystem(artifact, jarPath.toString());
                            ProcessBuilder builder = new ProcessBuilder(
                                    "unzip",
                                    "-o",
                                    jarPath.toString(),
                                    "-d",
                                    path.toString()
                            );
                            builder.inheritIO();
                            Process process = builder.start();
                            int exitCode = process.waitFor();
                            if(exitCode != 0) throw new RuntimeException("Failed to unzip javadoc for artifact: " + artifact);
                            Files.delete(jarPath);
                        }
                        catch (Exception ex) {
                            throw new RuntimeException("Failed to download artifact: " + artifact, ex);
                        }
                    }, finalExecutor))
                    .toList();
            CompletableFuture.allOf(futures1.toArray(new CompletableFuture[0])).join();

            // Generate index.html files
            Configuration config = new Configuration(Configuration.VERSION_2_3_34);
            config.setClassLoaderForTemplateLoading(Main.class.getClassLoader(), "");
            config.setDefaultEncoding("UTF-8");
            Template template = config.getTemplate("package-index.ftl");
            generateIndexHtml(sitePath, "/", 2, template, executor);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        finally {
            if(executor != null) executor.close();
        }
    }

    private static void generateIndexHtml(String currentPath, String relativePath, int depth, Template template, ExecutorService executor) throws IOException {
        if(depth == 0) return;
        Path path = Path.of(currentPath);
        Stream<Path> stream = null;
        List<String> dirs;
        try {
            stream = Files.list(path);
            dirs = stream
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to list directories in path: " + currentPath, ex);
        }
        finally {
            if(stream != null) stream.close();
        }

        BufferedWriter writer = null;
        try {
            writer = Files.newBufferedWriter(path.resolve("index.html"));
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("currentPath", relativePath);
            dataModel.put("directories", dirs);
            if(!relativePath.equals("/")) dataModel.put("parentPath", "..");
            template.process(dataModel, writer);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to generate index.html for path: " + currentPath, ex);
        }
        finally {
            if(writer != null) writer.close();
        }

        List<CompletableFuture<Void>> futures = dirs
                .stream()
                .map(dir -> CompletableFuture.runAsync(() -> {
                    try {
                        generateIndexHtml(path.resolve(dir).toString(), relativePath + dir + "/", depth - 1, template, executor);
                    }
                    catch (IOException ex) {
                        throw new RuntimeException("Failed to generate index.html for directory: " + dir, ex);
                    }
                }, executor))
                .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}