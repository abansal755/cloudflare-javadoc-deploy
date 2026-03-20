package in.co.akshitbansal.cloudflare.javadoc.deploy.util;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class PropertiesUtil {

    public static Properties loadProperties() {
        try {
            // 1. Load properties from application.properties file in the classpath
            Properties props = loadPropertiesFromResource("application.properties");

            // 2. Override with properties from S3 if enabled
            Properties s3Props = loadPropertiesFromS3();

            // 3. Override with application-local.properties if it exists in the classpath
            Properties localProps = loadPropertiesFromResource("application-local.properties");

            // 4. Override with system properties
            Properties systemProps = System.getProperties();

            Properties finalProps = new Properties();
            finalProps.putAll(props);
            finalProps.putAll(s3Props);
            finalProps.putAll(localProps);
            finalProps.putAll(systemProps);
            return finalProps;
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to load application properties", ex);
        }
    }

    private static Properties loadPropertiesFromResource(String fileName) throws IOException {
        Properties props = new Properties();
        @Cleanup InputStream stream = PropertiesUtil
                .class
                .getClassLoader()
                .getResourceAsStream(fileName);
        if (stream != null) props.load(stream);
        return props;
    }

    private static Properties loadPropertiesFromS3() throws IOException {
        // Only if config.s3.enabled system property is set to true, load properties from S3
        // Will only be used in production profile, so relying on aws iam role for creds
        boolean isEnabled = Boolean.parseBoolean(System.getProperty("config.s3.enabled"));
        if(!isEnabled) return new Properties();

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
        @Cleanup ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request);
        Properties props = new Properties();
        props.load(stream);
        log.info("Successfully loaded properties from S3. Bucket: {}, Key: {}", bucket, key);
        return props;
    }
}
