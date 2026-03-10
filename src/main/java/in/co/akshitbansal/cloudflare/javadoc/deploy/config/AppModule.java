package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class AppModule extends AbstractModule {

    private final Props props;

    @Provides
    @Singleton
    Props provideProps() {
        return props;
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
