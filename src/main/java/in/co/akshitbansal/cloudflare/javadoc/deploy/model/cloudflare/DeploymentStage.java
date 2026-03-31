package in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentStage {

    private String name;
    private String status;
}
