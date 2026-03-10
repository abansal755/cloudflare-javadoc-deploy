package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.TypeLiteral;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler;
import in.co.akshitbansal.cloudflare.javadoc.deploy.MDCExecutorService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.MavenCentralClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiredArgsConstructor
public class BeanFactory implements AutoCloseable {

    private final Map<TypeLiteral<?>, Object> beans = new ConcurrentHashMap<>();

    private final boolean DISABLE_SNAPSHOTS;
    private final boolean DISABLE_CLOUDFLARE_DEPLOYMENT;
    private final boolean DISABLE_TEMP_FILE_DELETION;

    private final String CLOUDFLARE_API_TOKEN;
    private final String CLOUDFLARE_PROJECT_NAME;

    public RetryRegistry getRetryRegistry() {
        return (RetryRegistry) beans.computeIfAbsent(
                new TypeLiteral<RetryRegistry>() {},
                type -> RetryRegistry.of(RetryConfig
                        .custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofSeconds(3))
                        .retryExceptions(RetryableException.class)
                        .build()
                ));
    }

    public RetryDecoratorService getRetryDecoratorService() {
        RetryRegistry retryRegistry = getRetryRegistry();
        return (RetryDecoratorService) beans.computeIfAbsent(
                new TypeLiteral<RetryDecoratorService>() {},
                type -> new RetryDecoratorService(retryRegistry)
        );
    }

    public HttpClient getHttpClient() {
        return (HttpClient) beans.computeIfAbsent(
                new TypeLiteral<HttpClient>() {},
                type -> HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL) // Always follow redirects, except from HTTPS URLs to HTTP URLs
                .build());
    }

    @SuppressWarnings("unchecked")
    public List<MavenRepository> getRepositories() {
        return (List<MavenRepository>) beans.computeIfAbsent(
                new TypeLiteral<List<MavenRepository>>() {},
                type -> {
                    List<MavenRepository> repositories = new ArrayList<>();
                    // For stable releases
                    repositories.add(new MavenRepository("https://repo1.maven.org/maven2", false));
                    if(!DISABLE_SNAPSHOTS)
                        repositories.add(new MavenRepository("https://central.sonatype.com/repository/maven-snapshots", true));
                    return repositories;
        });
    }

    public MavenCentralClient getMavenCentralClient() {
        HttpClient httpClient = getHttpClient();
        List<MavenRepository> repositories = getRepositories();
        RetryDecoratorService retryDecoratorService = getRetryDecoratorService();
        return (MavenCentralClient) beans.computeIfAbsent(
                new TypeLiteral<MavenCentralClient>() {},
                type -> new MavenCentralClient(httpClient, repositories, retryDecoratorService)
        );
    }

    public ExecutorService getExecutor() {
        return (ExecutorService) beans.computeIfAbsent(
                new TypeLiteral<ExecutorService>() {},
                type -> new MDCExecutorService(Executors.newVirtualThreadPerTaskExecutor())
        );
    }

    public MavenCentralService getMavenCentralService() {
        ExecutorService executor = getExecutor();
        MavenCentralClient mavenCentralClient = getMavenCentralClient();
        return (MavenCentralService) beans.computeIfAbsent(
                new TypeLiteral<MavenCentralService>() {},
                type -> new MavenCentralService(executor, mavenCentralClient)
        );
    }

    public IndexHtmlGeneratingService getIndexHtmlGeneratingService() {
        ExecutorService executor = getExecutor();
        return (IndexHtmlGeneratingService) beans.computeIfAbsent(
                new TypeLiteral<IndexHtmlGeneratingService>() {},
                type -> {
                    Configuration config = new Configuration(Configuration.VERSION_2_3_34);
                    config.setClassLoaderForTemplateLoading(LambdaHandler.class.getClassLoader(), "");
                    config.setDefaultEncoding("UTF-8");
                    Template template;
                    try {
                        template = config.getTemplate("package-index.ftl");
                    }
                    catch (Exception ex) {
                        throw new RuntimeException("Failed to load freemarker template: package-index.ftl", ex);
                    }
                    return new IndexHtmlGeneratingService(executor, template);
                }
        );
    }

    public CloudflareService getCloudflareService() {
        RetryDecoratorService retryDecoratorService = getRetryDecoratorService();
        return (CloudflareService) beans.computeIfAbsent(
                new TypeLiteral<CloudflareService>() {},
                type -> new CloudflareService(retryDecoratorService, CLOUDFLARE_API_TOKEN, CLOUDFLARE_PROJECT_NAME)
        );
    }

    public FilesystemService getFilesystemService() {
        return (FilesystemService) beans.computeIfAbsent(
                new TypeLiteral<FilesystemService>() {},
                type -> new FilesystemService()
        );
    }

    public DeploymentService getDeploymentService() {
        MavenCentralService mavenCentralService = getMavenCentralService();
        IndexHtmlGeneratingService indexHtmlGeneratingService = getIndexHtmlGeneratingService();
        CloudflareService cloudflareService = getCloudflareService();
        FilesystemService filesystemService = getFilesystemService();
        return (DeploymentService) beans.computeIfAbsent(
                new TypeLiteral<DeploymentService>() {},
                type -> new DeploymentService(
                        mavenCentralService,
                        indexHtmlGeneratingService,
                        cloudflareService,
                        filesystemService,
                        DISABLE_CLOUDFLARE_DEPLOYMENT,
                        DISABLE_TEMP_FILE_DELETION
                )
        );
    }

    @Override
    public void close() {
        ExecutorService executor = (ExecutorService) beans.get(new TypeLiteral<ExecutorService>() {});
        if(executor != null) executor.close();
    }
}
