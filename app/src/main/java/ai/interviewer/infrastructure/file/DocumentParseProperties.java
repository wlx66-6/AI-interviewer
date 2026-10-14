package ai.interviewer.infrastructure.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

import lombok.Data;

/** 文档解析的超时与有界线程池配置。 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.document-parse")
public class DocumentParseProperties {

  @NotNull private Duration timeout = Duration.ofMinutes(2);

  @Min(1) @Max(16) private int poolSize = 2;

  @Min(1) private int queueCapacity = 20;
}
