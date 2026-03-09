package in.co.akshitbansal.cloudflare.javadoc.deploy.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class MavenArtifact extends MavenPackage {

    private String version;

    public MavenArtifact(String groupId, String artifactId, String version) {
        super(groupId, artifactId);
        this.version = version;
    }
}
