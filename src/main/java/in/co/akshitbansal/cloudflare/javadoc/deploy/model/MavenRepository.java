package in.co.akshitbansal.cloudflare.javadoc.deploy.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
public class MavenRepository {

    private final String baseUrl;
    private final boolean snapshotRepository;
}
