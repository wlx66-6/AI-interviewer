package ai.interviewer.common.async.recovery;

import ai.interviewer.common.async.AsyncTaskRecoveryProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * 简历分析卡住任务恢复配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.async.recovery.resume-analyze")
public class ResumeAnalysisRecoveryProperties extends AsyncTaskRecoveryProperties {
}
