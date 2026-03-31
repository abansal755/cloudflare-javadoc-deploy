package in.co.akshitbansal.cloudflare.javadoc.deploy.model.cloudflare;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HashesReq {

    private List<String> hashes;
}
