package in.co.akshitbansal.cloudflare.javadoc.deploy.model.freemarker;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PackageIndexTemplateModel {

    private final String currentPath;
    private final List<String> directories;
    private final boolean showSnapshotToggle;
    private boolean showParentLink;
}
