package ai.interviewer.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.agent-utils")
public class AgentUtilsProperties {

    private String skillsRoot = "classpath:skills";
}
