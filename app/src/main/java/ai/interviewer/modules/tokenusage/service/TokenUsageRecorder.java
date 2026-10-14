package ai.interviewer.modules.tokenusage.service;

import ai.interviewer.common.ai.LlmUsageSink;
import ai.interviewer.common.transaction.TransactionalExecutor;
import ai.interviewer.modules.tokenusage.model.LlmTokenUsageEntity;
import ai.interviewer.modules.tokenusage.repository.LlmTokenUsageRepository;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link LlmUsageSink} 的落库实现。
 *
 * <p>本类是 LLM 主链路旁的旁路写入点，因此遵守两条纪律：
 *
 * <ol>
 *   <li>写入走 {@link TransactionalExecutor#runRequiresNew}，与调用方事务解耦——即使调用方事务随后回滚，
 *       用量记录也已提交；同时避免长事务持有数据库连接。
 *   <li>任何写入异常只记 {@code warn} 日志后吞掉，<b>绝不允许影响 LLM 调用本身</b>。
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageRecorder implements LlmUsageSink {

  private static final String FALLBACK_PROVIDER = "default";

  private final LlmTokenUsageRepository repository;
  private final TransactionalExecutor transactionalExecutor;

  @Override
  public void record(LlmUsage usage) {
    try {
      LlmTokenUsageEntity entity =
          LlmTokenUsageEntity.builder()
              .providerId(truncate(resolveProvider(usage.providerId()), 64))
              .model(truncate(usage.model(), 128))
              .kind(truncate(usage.kind(), 16))
              .source(truncate(usage.source(), 32))
              .promptTokens(nonNegative(usage.promptTokens()))
              .completionTokens(nonNegative(usage.completionTokens()))
              .totalTokens(nonNegative(usage.totalTokens()))
              .latencyMs(usage.latencyMs())
              .success(usage.success())
              .build();
      transactionalExecutor.runRequiresNew(() -> repository.save(entity));
    } catch (Exception e) {
      log.warn("[TokenUsage] 用量写入失败，已忽略: {}", e.toString());
    }
  }

  private static String resolveProvider(String providerId) {
    return providerId == null || providerId.isBlank() ? FALLBACK_PROVIDER : providerId;
  }

  private static int nonNegative(int value) {
    return Math.max(value, 0);
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
