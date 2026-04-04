package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.VersionComparator;
import in.co.akshitbansal.cloudflare.javadoc.deploy.client.email.EmailClient;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenArtifact;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.freemarker.DeploymentStatusEmailTemplateModel;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.freemarker.FailureTraceSectionTemplateModel;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.freemarker.ResolvedPackageArtifactsTemplateModel;
import jakarta.inject.Named;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class EmailService {

    private static final String CODEBASE_PACKAGE_PREFIX = "in.co.akshitbansal.cloudflare.javadoc.deploy.";
    private static final int MAX_DISPLAYED_ARTIFACTS_PER_PACKAGE = 10;

    private final EmailClient emailClient;
    private final Template freemarkerTemplate;

    private final String STATUS_EMAIL_RECIPIENT;
    private final String SITE_URL;

    @Inject
    public EmailService(
            EmailClient emailClient,
            @Named("statusEmailTemplate") Template freemarkerTemplate,
            Config config
    ) {
        this.emailClient = emailClient;
        this.freemarkerTemplate = freemarkerTemplate;
        this.STATUS_EMAIL_RECIPIENT = config.getString("stage.status-email.recipient");
        this.SITE_URL = config.getString("stage.status-email.site-url");
    }

    public void sendDeploymentStatusEmail(
            boolean success,
            Throwable failure,
            @NonNull List<MavenPackage> packages,
            @NonNull List<MavenArtifact> artifacts,
            @NonNull String requestId,
            @NonNull UUID correlationId
    ) {
        try {
            String subject = "Cloudflare Javadoc Deployment " + (success ? "Succeeded" : "Failed");
            String emailContent = generateEmailContent(success, failure, packages, artifacts, requestId, correlationId);
            emailClient.sendEmail(STATUS_EMAIL_RECIPIENT, subject, emailContent);

            log.info("Sending deployment status email to {}", STATUS_EMAIL_RECIPIENT);
        }
        catch (Exception ex) {
            // Silently catch any exceptions to avoid affecting the main deployment flow, but log the error for debugging purposes
            log.error("Failed to send deployment status email", ex);
        }
    }

    private String generateEmailContent(
            boolean success,
            Throwable failure,
            @NonNull List<MavenPackage> packages,
            @NonNull List<MavenArtifact> artifacts,
            String requestId,
            UUID correlationId
    ) throws TemplateException, IOException {
        StringWriter writer = new StringWriter();
        DeploymentStatusEmailTemplateModel templateModel = DeploymentStatusEmailTemplateModel
                .builder()
                .success(success)
                .failureTraceSections(buildFailureTraceSections(failure))
                .packages(packages)
                .resolvedPackageArtifacts(getResolvedPackageArtifacts(artifacts))
                .siteUrl(SITE_URL)
                .requestId(requestId)
                .correlationId(correlationId.toString())
                .build();
        freemarkerTemplate.process(templateModel, writer);
        return writer.toString();
    }

    private List<ResolvedPackageArtifactsTemplateModel> getResolvedPackageArtifacts(@NonNull List<MavenArtifact> artifacts) {
        Map<String , List<String>> artifactsByPackage = new HashMap<>();
        for(MavenArtifact artifact: artifacts) {
            String packageCoordinate = toPackageCoordinate(new MavenPackage(artifact.getGroupId(), artifact.getArtifactId()));
            artifactsByPackage.compute(packageCoordinate, (key, existingArtifacts) -> {
                if(existingArtifacts == null)
                    existingArtifacts = new ArrayList<>();
                existingArtifacts.add(artifact.getVersion());
                return existingArtifacts;
            });
        }
        for(List<String> versions: artifactsByPackage.values())
            versions.sort(new VersionComparator());
        return artifactsByPackage
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ResolvedPackageArtifactsTemplateModel
                        .builder()
                        .packageCoordinate(entry.getKey())
                        .versions(getDisplayedVersions(entry.getValue()))
                        .hasMoreVersions(entry.getValue().size() > MAX_DISPLAYED_ARTIFACTS_PER_PACKAGE)
                        .build())
                .toList();
    }

    private List<String> getDisplayedVersions(@NonNull List<String> versions) {
        return versions
                .stream()
                .limit(MAX_DISPLAYED_ARTIFACTS_PER_PACKAGE)
                .toList();
    }

    private String toPackageCoordinate(@NonNull MavenPackage mavenPackage) {
        return mavenPackage.getGroupId() + ":" + mavenPackage.getArtifactId();
    }

    private List<FailureTraceSectionTemplateModel> buildFailureTraceSections(Throwable failure) {
        if(failure == null) return Collections.emptyList();

        List<FailureTraceSectionTemplateModel> sections = new ArrayList<>();
        appendThrowableSection(sections, failure, false);
        return sections;
    }

    private void appendThrowableSection(List<FailureTraceSectionTemplateModel> sections, Throwable throwable, boolean causedBy) {
        List<String> codebaseFrames = Arrays
                .stream(throwable.getStackTrace())
                .filter(this::isCodebaseFrame)
                .map(frame -> frame.getClassName() + "." + frame.getMethodName()
                        + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")")
                .collect(Collectors.toList());

        sections.add(FailureTraceSectionTemplateModel
                .builder()
                .isCausedBy(causedBy)
                .exceptionClassName(throwable.getClass().getName())
                .message(throwable.getMessage())
                .codebaseFrames(codebaseFrames)
                .build());

        Throwable cause = throwable.getCause();
        if(cause != null) {
            appendThrowableSection(sections, cause, true);
        }
    }

    private boolean isCodebaseFrame(StackTraceElement frame) {
        return frame.getClassName().startsWith(CODEBASE_PACKAGE_PREFIX);
    }
}
