package ai.interviewer.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 限流身份解析配置。
 *
 * <p>默认不采信 X-Forwarded-For：本仓库的 compose 里没有反向代理，请求直接打到后端，
 * 这个头由客户端自己决定，采信它等于每个请求都能换一个限流桶。只有前面确实挂了会覆盖该头的代理时才打开。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

  private boolean trustForwardedHeaders = false;
}
