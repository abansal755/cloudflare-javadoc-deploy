package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.DeploymentService;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Binding DeploymentService so that is initialized eagerly at application startup
        bind(DeploymentService.class);
    }

    @Provides
    @Singleton
    Props provideProps() {
        boolean DISABLE_SNAPSHOTS = Boolean.parseBoolean(System.getenv("DISABLE_SNAPSHOTS"));
        boolean DISABLE_CLOUDFLARE_DEPLOYMENT = Boolean.parseBoolean(System.getenv("DISABLE_CLOUDFLARE_DEPLOYMENT"));
        boolean DISABLE_TEMP_FILE_DELETION = Boolean.parseBoolean(System.getenv("DISABLE_TEMP_FILE_DELETION"));

        String CLOUDFLARE_API_TOKEN = System.getenv("CLOUDFLARE_API_TOKEN");
        if(!DISABLE_CLOUDFLARE_DEPLOYMENT && CLOUDFLARE_API_TOKEN == null) {
            throw new IllegalArgumentException("Cloudflare API token must be provided as environment variable with key 'CLOUDFLARE_API_TOKEN'");
        }

        String CLOUDFLARE_PROJECT_NAME = System.getenv("CLOUDFLARE_PROJECT_NAME");
        if(!DISABLE_CLOUDFLARE_DEPLOYMENT && CLOUDFLARE_PROJECT_NAME == null) {
            throw new IllegalArgumentException("Cloudflare project name must be provided as system property with key 'CLOUDFLARE_PROJECT_NAME'");
        }

        return new Props(
                DISABLE_SNAPSHOTS,
                DISABLE_CLOUDFLARE_DEPLOYMENT,
                DISABLE_TEMP_FILE_DELETION,
                CLOUDFLARE_API_TOKEN,
                CLOUDFLARE_PROJECT_NAME
        );
    }

    @Provides
    @Singleton
    RetryRegistry provideRetryRegistry() {
        return RetryRegistry.of(RetryConfig
                .custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(3))
                .retryExceptions(RetryableException.class)
                .build()
        );
    }

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL) // Always follow redirects, except from HTTPS URLs to HTTP URLs
                .build();
    }

    @Provides
    @Singleton
    List<MavenRepository> provideRepositories(Props props) {
        List<MavenRepository> repositories = new ArrayList<>();
        // For stable releases
        repositories.add(new MavenRepository("https://repo1.maven.org/maven2", false));
        if(!props.DISABLE_SNAPSHOTS)
            repositories.add(new MavenRepository("https://central.sonatype.com/repository/maven-snapshots", true));
        return Collections.unmodifiableList(repositories);
    }

    @Provides
    @Singleton
    Template provideTemplate() {
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
        return template;
    }
}
