package in.co.akshitbansal.cloudflare.javadoc.deploy.client.email;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Singleton
public class AwsSesEmailClient implements EmailClient {

    private final SesClient sesClient;
    private final String STATUS_EMAIL_SENDER;

    @Inject
    public AwsSesEmailClient(SesClient sesClient, Config config) {
        this.sesClient = sesClient;
        this.STATUS_EMAIL_SENDER = config.getString("stage.status-email.sender");
    }

    @Override
    public void sendEmail(String recipient, String subject, String body) {
        // Recipient
        Destination destination = Destination.builder()
                .toAddresses(recipient)
                .build();
        // Subject
        Content emailSubject = Content.builder()
                .data(subject)
                .build();

        // Body
        Content content = Content.builder()
                .data(body)
                .build();
        Body emailBody = Body.builder()
                .html(content)
                .build();
        Message message = Message.builder()
                .subject(emailSubject)
                .body(emailBody)
                .build();

        SendEmailRequest emailRequest = SendEmailRequest
                .builder()
                .message(message)
                .destination(destination)
                .source(STATUS_EMAIL_SENDER)
                .build();
        sesClient.sendEmail(emailRequest);
    }
}
