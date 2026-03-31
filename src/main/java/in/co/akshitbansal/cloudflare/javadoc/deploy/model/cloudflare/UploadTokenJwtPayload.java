package in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadTokenJwtPayload {

    @JsonProperty("exp")
    private long expireAt;
}
