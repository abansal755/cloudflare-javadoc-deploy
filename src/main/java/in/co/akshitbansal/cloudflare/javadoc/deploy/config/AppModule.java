package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.DeploymentService;
import jakarta.inject.Named;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

import java.net.http.HttpClient;
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
        boolean DISABLE_STATUS_EMAIL = Boolean.parseBoolean(System.getenv("DISABLE_STATUS_EMAIL"));

        String CLOUDFLARE_API_TOKEN = System.getenv("CLOUDFLARE_API_TOKEN");
        if(!DISABLE_CLOUDFLARE_DEPLOYMENT && CLOUDFLARE_API_TOKEN == null) {
            throw new IllegalArgumentException("Cloudflare API token must be provided as environment variable with key 'CLOUDFLARE_API_TOKEN'");
        }

        String CLOUDFLARE_PROJECT_NAME = System.getenv("CLOUDFLARE_PROJECT_NAME");
        if(!DISABLE_CLOUDFLARE_DEPLOYMENT && CLOUDFLARE_PROJECT_NAME == null) {
            throw new IllegalArgumentException("Cloudflare project name must be provided as system property with key 'CLOUDFLARE_PROJECT_NAME'");
        }

        String STATUS_EMAIL_RECIPIENT = System.getenv("STATUS_EMAIL_RECIPIENT");
        String STATUS_EMAIL_SENDER = System.getenv("STATUS_EMAIL_SENDER");
        String SITE_URL = System.getenv("SITE_URL");
        if(!DISABLE_STATUS_EMAIL) {
            if(STATUS_EMAIL_RECIPIENT == null) {
                throw new IllegalArgumentException("Status email recipient must be provided as environment variable with key 'STATUS_EMAIL_RECIPIENT'");
            }
            if(STATUS_EMAIL_SENDER == null) {
                throw new IllegalArgumentException("Status email sender must be provided as environment variable with key 'STATUS_EMAIL_SENDER'");
            }
            if(SITE_URL == null) {
                throw new IllegalArgumentException("Site URL must be provided as environment variable with key 'SITE_URL'");
            }
        }

        return new Props(
                DISABLE_SNAPSHOTS,
                DISABLE_CLOUDFLARE_DEPLOYMENT,
                DISABLE_TEMP_FILE_DELETION,
                DISABLE_STATUS_EMAIL,
                CLOUDFLARE_API_TOKEN,
                CLOUDFLARE_PROJECT_NAME,
                STATUS_EMAIL_RECIPIENT,
                STATUS_EMAIL_SENDER,
                SITE_URL
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
    Configuration provideFreemarkerConfiguration() {
        Configuration config = new Configuration(Configuration.VERSION_2_3_34);
        config.setClassLoaderForTemplateLoading(LambdaHandler.class.getClassLoader(), "");
        config.setDefaultEncoding("UTF-8");
        return config;
    }

    @Provides
    @Singleton
    @Named("indexHtmlTemplate")
    Template provideIndexHtmlTemplate(Configuration config) {
        try {
            return config.getTemplate("package-index.ftl");
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to load freemarker template: package-index.ftl", ex);
        }
    }

    @Provides
    @Singleton
    @Named("statusEmailTemplate")
    Template provideStatusEmailTemplate(Configuration config) {
        try {
            return config.getTemplate("status-email.ftl");
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to load freemarker template: status-email.ftl", ex);
        }
    }

    @Provides
    @Singleton
    SesClient provideSesClient() {
        return SesClient
                .builder()
                .region(Region.AP_SOUTH_2)
                .build();
    }
}
