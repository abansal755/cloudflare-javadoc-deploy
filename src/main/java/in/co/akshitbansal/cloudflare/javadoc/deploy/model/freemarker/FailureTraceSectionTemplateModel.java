package in.co.akshitbansal.cloudflare.javadoc.deploy.model.freemarker;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FailureTraceSectionTemplateModel {

    private boolean isCausedBy;
    private final String exceptionClassName;
    private final String message;
    private final List<String> codebaseFrames;
}
