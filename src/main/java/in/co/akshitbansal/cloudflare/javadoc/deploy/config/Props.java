package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Props {

    public final boolean DISABLE_SNAPSHOTS;
    public final boolean DISABLE_CLOUDFLARE_DEPLOYMENT;
    public final boolean DISABLE_TEMP_FILE_DELETION;

    public final String CLOUDFLARE_API_TOKEN;
    public final String CLOUDFLARE_PROJECT_NAME;
}
