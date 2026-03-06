package in.co.akshitbansal.cloudflare.javadoc.deploy.model;

import lombok.Data;

import java.util.List;

@Data
public class LambdaInput {

    private List<MavenPackage> packages;
}
