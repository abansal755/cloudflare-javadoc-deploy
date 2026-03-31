package in.co.akshitbansal.cloudflare.javadoc.deploy.service.cloudflare;

import com.typesafe.config.Config;
import in.co.akshitbansal.cloudflare.javadoc.deploy.annotation.Retry;
import in.co.akshitbansal.cloudflare.javadoc.deploy.exception.RetryableException;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Map;

@Slf4j
public class CloudflareDeploymentServiceWranglerImpl implements CloudflareDeploymentService {

    private final String CLOUDFLARE_API_TOKEN;
    private final String CLOUDFLARE_PROJECT_NAME;

    public CloudflareDeploymentServiceWranglerImpl(Config config) {
        this.CLOUDFLARE_API_TOKEN = config.getString("stage.cloudflare-deployment.api-token");
        this.CLOUDFLARE_PROJECT_NAME = config.getString("stage.cloudflare-deployment.project-name");
    }

    @Override
    @Retry
    public void deploy(Path sitePath) {
        try {
            log.info("Started deploying {} to Cloudflare Pages project {}", sitePath, CLOUDFLARE_PROJECT_NAME);

            // Build the command to deploy using Wrangler CLI
            ProcessBuilder builder = new ProcessBuilder(
                    "wrangler",
                    "pages",
                    "deploy",
                    sitePath.toString(),
                    "--project-name=" + CLOUDFLARE_PROJECT_NAME
            );
            // Set the working directory to /tmp which is the only writable directory in the Lambda execution environment. This is required for Wrangler CLI to create its configuration file and cache.
            String workDir = sitePath.resolve("../").toString();
            builder.directory(new File(workDir));

            // Set the environment variable for the API token
            Map<String,String> env = builder.environment();
            env.put("CLOUDFLARE_API_TOKEN", CLOUDFLARE_API_TOKEN);

            Process process = builder.start();
            int exitCode = process.waitFor();
            if(exitCode != 0) {
                @Cleanup InputStream errorStream = process.getErrorStream();
                String errorOutput = new String(errorStream.readAllBytes());
                throw new RetryableException(MessageFormat.format(
                        "Wrangler CLI failed exited with code {0}. Error output: {1}",
                        exitCode, errorOutput
                ));
            }

            @Cleanup InputStream inputStream = process.getInputStream();
            String output = new String(inputStream.readAllBytes());
            log.info("Wrangler CLI output: {}", output);

            log.info("Completed deploying {} to Cloudflare Pages project {}", sitePath, CLOUDFLARE_PROJECT_NAME);
        }
        catch (RetryableException ex) {
            throw ex; // Rethrow retryable exceptions as is
        }
        catch (IOException | SecurityException ex) {
            throw new RetryableException("Retryable exception occurred while deploying site to Cloudflare Pages project: " + CLOUDFLARE_PROJECT_NAME, ex);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new RuntimeException("Thread was interrupted while deploying site to Cloudflare Pages project: " + CLOUDFLARE_PROJECT_NAME, ex);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to deploy site to Cloudflare Pages project: " + CLOUDFLARE_PROJECT_NAME, ex);
        }
    }
}
