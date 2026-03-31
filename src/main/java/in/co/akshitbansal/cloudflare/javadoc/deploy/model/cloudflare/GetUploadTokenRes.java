package in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetUploadTokenRes {

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private String jwt;
    }
}
