package in.co.akshitbansal.cloudflare.javadoc.deploy.service.cloudflare;

import java.nio.file.Path;

public interface CloudflareDeploymentService {

    void deploy(Path sitePath);
}
