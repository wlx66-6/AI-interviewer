package ai.interviewer.modules.resume.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.resume.analysis")
public class ResumeAnalysisProperties {

    private String systemPromptPath = "classpath:prompts/resume-analysis-system.st";
    private String userPromptPath = "classpath:prompts/resume-analysis-user.st";
}
