package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

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

    private final Map<String, Object> beans = new ConcurrentHashMap<>();

    private final boolean DISABLE_SNAPSHOTS;
    private final boolean DISABLE_CLOUDFLARE_DEPLOYMENT;
    private final boolean DISABLE_TEMP_FILE_DELETION;

    private final String CLOUDFLARE_API_TOKEN;
    private final String CLOUDFLARE_PROJECT_NAME;

    public RetryRegistry getRetryRegistry() {
        return (RetryRegistry) beans.computeIfAbsent("retryRegistry", name -> RetryRegistry.of(RetryConfig
                .custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(3))
                .retryExceptions(RetryableException.class)
                .build()));
    }

    public RetryDecoratorService getRetryDecoratorService() {
        return (RetryDecoratorService) beans.computeIfAbsent(
                "retryDecoratorService",
                name -> new RetryDecoratorService(getRetryRegistry())
        );
    }

    public HttpClient getHttpClient() {
        return (HttpClient) beans.computeIfAbsent("httpClient", name -> HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL) // Always follow redirects, except from HTTPS URLs to HTTP URLs
                .build());
    }

    @SuppressWarnings("unchecked")
    public List<MavenRepository> getRepositories() {
        return (List<MavenRepository>) beans.computeIfAbsent("repositories", name -> {
            List<MavenRepository> repositories = new ArrayList<>();
            // For stable releases
            repositories.add(new MavenRepository("https://repo1.maven.org/maven2", false));
            if(!DISABLE_SNAPSHOTS)
                repositories.add(new MavenRepository("https://central.sonatype.com/repository/maven-snapshots", true));
            return repositories;
        });
    }

    public MavenCentralClient getMavenCentralClient() {
        return (MavenCentralClient) beans.computeIfAbsent(
                "mavenCentralClient",
                name -> new MavenCentralClient(getHttpClient(), getRepositories(), getRetryDecoratorService())
        );
    }

    public ExecutorService getExecutor() {
        return (ExecutorService) beans.computeIfAbsent(
                "executor",
                name -> new MDCExecutorService(Executors.newVirtualThreadPerTaskExecutor())
        );
    }

    public MavenCentralService getMavenCentralService() {
        return (MavenCentralService) beans.computeIfAbsent(
                "mavenCentralService",
                name -> new MavenCentralService(getExecutor(), getMavenCentralClient())
        );
    }

    public IndexHtmlGeneratingService getIndexHtmlGeneratingService() {
        return (IndexHtmlGeneratingService) beans.computeIfAbsent(
                "indexHtmlGeneratingService",
                name -> {
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
                    return new IndexHtmlGeneratingService(getExecutor(), template);
                }
        );
    }

    public CloudflareService getCloudflareService() {
        return (CloudflareService) beans.computeIfAbsent(
                "cloudflareService",
                name -> new CloudflareService(CLOUDFLARE_API_TOKEN, CLOUDFLARE_PROJECT_NAME)
        );
    }

    public FilesystemService getFilesystemService() {
        return (FilesystemService) beans.computeIfAbsent("filesystemService", name -> new FilesystemService());
    }

    public DeploymentService getDeploymentService() {
        return (DeploymentService) beans.computeIfAbsent(
                "deploymentService",
                name -> new DeploymentService(
                        getMavenCentralService(),
                        getIndexHtmlGeneratingService(),
                        getCloudflareService(),
                        getFilesystemService(),
                        DISABLE_CLOUDFLARE_DEPLOYMENT,
                        DISABLE_TEMP_FILE_DELETION
                )
        );
    }

    @Override
    public void close() {
        ExecutorService executor = (ExecutorService) beans.get("executor");
        if(executor != null) executor.close();
    }
}
