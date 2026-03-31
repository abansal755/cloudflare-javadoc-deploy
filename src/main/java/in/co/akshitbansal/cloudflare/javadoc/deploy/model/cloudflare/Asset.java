package in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class Asset {

    private String key;
    private String value;
    private boolean base64;
    private Metadata metadata;

    public Asset(String key, String value, String contentType) {
        this.key = key;
        this.value = value;
        this.base64 = true;
        this.metadata = new Metadata(contentType);
    }

    @Data
    @AllArgsConstructor
    public static class Metadata {

        private String contentType;
    }
}
