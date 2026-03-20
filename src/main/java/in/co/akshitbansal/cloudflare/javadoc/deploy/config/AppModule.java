package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.FailFastHttpClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler;
import in.co.akshitbansal.cloudflare.javadoc.deploy.MDCExecutorService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.ResourcesLifecycleManager;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.SchedulingService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.util.PropertiesUtil;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.ses.SesClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind services as eager singletons to ensure they are initialized at application startup
        bind(SchedulingService.class).asEagerSingleton();
        // ResourcesLifecycleManager is responsible for closing resources like ExecutorService and AWS clients, so it should also be an eager singleton
        bind(ResourcesLifecycleManager.class).asEagerSingleton();
        Names.bindProperties(binder(), PropertiesUtil.loadProperties());
    }

    @Provides
    @Singleton
    FailFastHttpClient provideHttpClient() {
        return FailFastHttpClient
                .newInstance(builder -> builder
                        .followRedirects(HttpClient.Redirect.NORMAL) // Always follow redirects, except from HTTPS URLs to HTTP URLs
                );
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
    ObjectMapper provideObjectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @Singleton
    ExecutorService provideExecutor() {
        return new MDCExecutorService(Executors.newVirtualThreadPerTaskExecutor());
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

    @Provides
    @Singleton
    SchedulerClient provideSchedulerClient(AwsCredentialsProvider awsCredentialsProvider) {
        return SchedulerClient
                .builder()
                .region(Region.AP_SOUTH_2)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }
}
