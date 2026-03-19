package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.DeploymentService;
import jakarta.inject.Named;
import lombok.Cleanup;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Binding DeploymentService so that is initialized eagerly at application startup
        bind(DeploymentService.class);
        Names.bindProperties(binder(), loadProperties());
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
    List<MavenRepository> provideRepositories(
            @Named("repository.central.base-url") String CENTRAL_BASE_URL,
            @Named("repository.snapshot.base-url") String SNAPSHOT_BASE_URL,
            @Named("repository.snapshot.disabled") String DISABLE_SNAPSHOTS
    ) {
        List<MavenRepository> repositories = new ArrayList<>();
        // For stable releases
        repositories.add(new MavenRepository(CENTRAL_BASE_URL, false));
        if(!Boolean.parseBoolean(DISABLE_SNAPSHOTS))
            repositories.add(new MavenRepository(SNAPSHOT_BASE_URL, true));
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
    List<String> provideActiveProfiles(@Named("profile.active") String PROFILES) {
        return List.of(PROFILES.split(","));
    }

    @Provides
    @Singleton
    AwsCredentialsProvider provideAwsCredentialsProvider(
            @Named("aws.access-key-id") String AWS_ACCESS_KEY_ID,
            @Named("aws.secret-access-key") String AWS_SECRET_ACCESS_KEY,
            List<String> activeProfiles
    ) {
        for(String profile: activeProfiles) {
            if(profile.equals("local")) return AwsCredentialsProviderChain.of(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY))
            );
            if(profile.equals("production")) return AwsCredentialsProviderChain.of(
                    DefaultCredentialsProvider.builder().build()
            );
        }
        throw new RuntimeException("No valid active profile found for AWS credentials provider configuration. Active profiles: " + activeProfiles);
    }

    @Provides
    @Singleton
    SesClient provideSesClient(AwsCredentialsProvider awsCredentialsProvider) {
        return SesClient
                .builder()
                .region(Region.AP_SOUTH_2)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    private Properties loadProperties() {
        try {
            // Load properties from application.properties file in the classpath
            Properties props = loadProperties("application.properties");
            // Override with application-local.properties if it exists in the classpath
            Properties localProps = loadProperties("application-local.properties");

            // Override with system properties
            Properties systemProps = System.getProperties();

            Properties finalProps = new Properties();
            finalProps.putAll(props);
            finalProps.putAll(localProps);
            finalProps.putAll(systemProps);
            return finalProps;
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to load application properties", ex);
        }
    }

    private Properties loadProperties(String fileName) throws IOException {
        Properties props = new Properties();
        @Cleanup InputStream stream = getClass()
                .getClassLoader()
                .getResourceAsStream(fileName);
        if (stream != null) props.load(stream);
        return props;
    }
}
