package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Props {

    public final boolean DISABLE_SNAPSHOTS;
    public final boolean DISABLE_CLOUDFLARE_DEPLOYMENT;
    public final boolean DISABLE_TEMP_FILE_DELETION;

    public final String CLOUDFLARE_API_TOKEN;
    public final String CLOUDFLARE_PROJECT_NAME;

    public static Props fromEnvVariables() {
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
}
