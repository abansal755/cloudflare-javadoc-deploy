package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import freemarker.template.Configuration;
import freemarker.template.Template;
import in.co.akshitbansal.cloudflare.javadoc.deploy.FailFastHttpClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.LambdaHandler;
import in.co.akshitbansal.cloudflare.javadoc.deploy.MDCExecutorService;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenRepository;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.ResourcesLifecycleManager;
import in.co.akshitbansal.cloudflare.javadoc.deploy.service.SchedulingService;
import jakarta.inject.Named;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
    List<MavenRepository> provideRepositories(Config config) {
        String CENTRAL_BASE_URL = config.getString("repository.central.base-url");
        String SNAPSHOT_BASE_URL = config.getString("repository.snapshot.base-url");
        boolean DISABLE_SNAPSHOTS = config.getBoolean("repository.snapshot.disabled");

        List<MavenRepository> repositories = new ArrayList<>();
        // For stable releases
        repositories.add(new MavenRepository(CENTRAL_BASE_URL, false));
        if(!DISABLE_SNAPSHOTS)
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
    AwsCredentialsProvider provideAwsCredentialsProvider(Config config) {
        String profile = config.getString("profile.active");
        if(profile.equals("local")) {
            String AWS_ACCESS_KEY_ID = config.getString("aws.access-key-id");
            String AWS_SECRET_ACCESS_KEY = config.getString("aws.secret-access-key");
            return AwsCredentialsProviderChain.of(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY))
            );
        }
        if(profile.equals("production")) return AwsCredentialsProviderChain.of(
                DefaultCredentialsProvider.builder().build()
        );
        throw new RuntimeException("No valid active profile found for AWS credentials provider configuration. Active profile: " + profile);
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

    @Provides
    @Singleton
    Config provideConfig() {
        Config application = ConfigFactory.parseResources("application.properties");
        Config s3 = getConfigFromS3();
        Config local = ConfigFactory.parseResourcesAnySyntax("application-local.properties");
        Config system = ConfigFactory.systemProperties();
        return system
                .withFallback(local)
                .withFallback(s3)
                .withFallback(application)
                .resolve();
    }

    private Config getConfigFromS3() {
        // Only if config.s3.enabled system property is set to true, load properties from S3
        // Will only be used in production profile, so relying on aws iam role for creds
        boolean isEnabled = Boolean.parseBoolean(System.getProperty("config.s3.enabled"));
        if(!isEnabled) return ConfigFactory.empty();

        String bucket = System.getProperty("config.s3.bucket");
        String key = System.getProperty("config.s3.key");
        if(bucket == null || key == null) {
            throw new RuntimeException("S3 bucket and key must be provided to load properties from S3. Bucket: " + bucket + ", Key: " + key);
        }
        log.info("Loading properties from S3. Bucket: {}, Key: {}", bucket, key);

        @Cleanup S3Client s3Client = S3Client
                .builder()
                .region(Region.AP_SOUTH_2)
                .build();
        GetObjectRequest request = GetObjectRequest
                .builder()
                .bucket(bucket)
                .key(key)
                .build();
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        String content = response.asUtf8String();
        return ConfigFactory.parseString(
                content,
                ConfigParseOptions.defaults().setSyntax(ConfigSyntax.PROPERTIES)
        );
    }
}
