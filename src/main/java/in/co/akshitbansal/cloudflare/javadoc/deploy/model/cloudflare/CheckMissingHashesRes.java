package in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckMissingHashesRes {

    private List<String> result;
}
