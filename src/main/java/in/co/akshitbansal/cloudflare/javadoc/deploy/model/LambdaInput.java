package in.co.akshitbansal.cloudflare.javadoc.deploy.model;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class LambdaInput {

    private List<String> deploymentIds;
    private UUID correlationId;
}
