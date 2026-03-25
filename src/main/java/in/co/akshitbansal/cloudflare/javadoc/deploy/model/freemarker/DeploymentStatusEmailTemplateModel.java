package in.co.akshitbansal.cloudflare.javadoc.deploy.model.freemarker;

import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DeploymentStatusEmailTemplateModel {

    private final boolean success;
    private final List<FailureTraceSectionTemplateModel> failureTraceSections;
    private final List<MavenPackage> packages;
    private final List<ResolvedPackageArtifactsTemplateModel> resolvedPackageArtifacts;
    private final String siteUrl;
    private final String requestId;
    private final String correlationId;
}
