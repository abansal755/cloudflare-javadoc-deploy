package in.co.akshitbansal.cloudflare.javadoc.deploy.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import in.co.akshitbansal.cloudflare.javadoc.deploy.config.Props;
import in.co.akshitbansal.cloudflare.javadoc.deploy.model.MavenPackage;
import jakarta.inject.Named;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Slf4j
public class AwsSesEmailService {

    private final SesClient sesClient;
    private final Template freemarkerTemplate;
    private final String STATUS_EMAIL_RECIPIENT;
    private final String STATUS_EMAIL_SENDER;
    private final String SITE_URL;

    @Inject
    public AwsSesEmailService(SesClient sesClient, @Named("statusEmailTemplate") Template freemarkerTemplate, Props props) {
        this.sesClient = sesClient;
        this.freemarkerTemplate = freemarkerTemplate;
        this.STATUS_EMAIL_RECIPIENT = props.STATUS_EMAIL_RECIPIENT;
        this.STATUS_EMAIL_SENDER = props.STATUS_EMAIL_SENDER;
        this.SITE_URL = props.SITE_URL;
    }

    public void sendDeploymentStatusEmail(boolean success, String errorMessage, @NonNull List<MavenPackage> packages, String requestId) {
        try {
            // Recipient
            Destination destination = Destination.builder()
                    .toAddresses(STATUS_EMAIL_RECIPIENT)
                    .build();
            // Subject
            Content subject = Content.builder()
                    .data("Cloudflare Javadoc Deployment " + (success ? "Succeeded" : "Failed"))
                    .build();

            // Body
            String emailContent = generateEmailContent(success, errorMessage, packages, requestId);
            Content content = Content.builder()
                    .data(emailContent)
                    .build();
            Body body = Body.builder()
                    .html(content)
                    .build();
            Message message = Message.builder()
                    .subject(subject)
                    .body(body)
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest
                    .builder()
                    .message(message)
                    .destination(destination)
                    .source(STATUS_EMAIL_SENDER)
                    .build();

            log.info("Sending deployment status email to {}", STATUS_EMAIL_RECIPIENT);
            sesClient.sendEmail(emailRequest);
        }
        catch (Exception ex) {
            // Silently catch any exceptions to avoid affecting the main deployment flow, but log the error for debugging purposes
            log.error("Failed to send deployment status email", ex);
        }
    }

    private String generateEmailContent(boolean success, String errorMessage, @NonNull List<MavenPackage> packages, String requestId) throws TemplateException, IOException {
        StringWriter writer = new StringWriter();
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("success", success);
        dataModel.put("errorMessage", errorMessage);
        dataModel.put("packages", packages);
        dataModel.put("siteUrl", SITE_URL);
        dataModel.put("requestId", requestId);
        freemarkerTemplate.process(dataModel, writer);
        return writer.toString();
    }
}
