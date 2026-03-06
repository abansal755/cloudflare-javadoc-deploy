package in.co.akshitbansal.cloudflare.javadoc.deploy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MavenPackage {

    private String groupId;
    private String artifactId;
}
