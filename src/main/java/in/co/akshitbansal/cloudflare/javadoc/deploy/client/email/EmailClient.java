package in.co.akshitbansal.cloudflare.javadoc.deploy.client.email;

public interface EmailClient {

    void sendEmail(String recipient, String subject, String body);
}
