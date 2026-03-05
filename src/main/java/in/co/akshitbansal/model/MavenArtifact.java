package in.co.akshitbansal.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MavenArtifact extends MavenPackage {

    private final String version;

    public MavenArtifact(String groupId, String artifactId, String version) {
        super(groupId, artifactId);
        this.version = version;
    }
}
