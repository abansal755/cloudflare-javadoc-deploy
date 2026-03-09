package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class CloudflareService {

    private final String CLOUDFLARE_API_TOKEN;
    private final String CLOUDFLARE_PROJECT_NAME;

    public void deploy(String sitePath, String workingDirectory) {
        try {
            log.info("Started deploying {} to Cloudflare Pages project {}", sitePath, CLOUDFLARE_PROJECT_NAME);

            // Build the command to deploy using Wrangler CLI
            ProcessBuilder builder = new ProcessBuilder(
                    "wrangler",
                    "pages",
                    "deploy",
                    sitePath,
                    "--project-name=" + CLOUDFLARE_PROJECT_NAME
            );
            // Set the working directory to /tmp which is the only writable directory in the Lambda execution environment. This is required for Wrangler CLI to create its configuration file and cache.
            builder.directory(new File(workingDirectory));

            // Set the environment variable for the API token
            Map<String,String> env = builder.environment();
            env.put("CLOUDFLARE_API_TOKEN", CLOUDFLARE_API_TOKEN);

            Process process = builder.start();
            int exitCode = process.waitFor();
            if(exitCode != 0) {
                @Cleanup InputStream errorStream = process.getErrorStream();
                String errorOutput = new String(errorStream.readAllBytes());
                throw new IllegalStateException(MessageFormat.format(
                        "Wrangler CLI failed exited with code {0}. Error output: {1}",
                        exitCode, errorOutput
                ));
            }

            @Cleanup InputStream inputStream = process.getInputStream();
            String output = new String(inputStream.readAllBytes());
            log.info("Wrangler CLI output: {}", output);

            log.info("Completed deploying {} to Cloudflare Pages project {}", sitePath, CLOUDFLARE_PROJECT_NAME);
        }
        catch (Exception ex) {
            throw new RuntimeException("Failed to deploy site to Cloudflare Pages project: " + CLOUDFLARE_PROJECT_NAME, ex);
        }
    }
}
