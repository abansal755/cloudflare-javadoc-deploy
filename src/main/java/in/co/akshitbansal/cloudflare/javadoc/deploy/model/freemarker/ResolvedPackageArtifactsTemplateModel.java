package in.co.akshitbansal.cloudflare.javadoc.deploy.model.freemarker;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ResolvedPackageArtifactsTemplateModel {

    private final String packageCoordinate;
    private final List<String> versions;
}
