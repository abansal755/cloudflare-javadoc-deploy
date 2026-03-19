package in.co.akshitbansal.cloudflare.javadoc.deploy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.co.akshitbansal.cloudflare.javadoc.deploy.enums.DeploymentStatus;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentStatusRes {

    private DeploymentStatus deploymentState;
}
