package ai.interviewer.common.ai;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link EmbeddingModel} 装饰器：统计向量化调用的 Token 用量。
 *
 * <p>只覆盖 {@code call(EmbeddingRequest)} 与 {@code embed(Document)} 两个抽象方法——接口上其余便捷方法
 * （{@code embed(List)}、{@code embedForResponse} 等）都是 default 实现并通过 {@code this} 转发，
 * 因此会自动落到这里的统计逻辑，无需逐一重写。
 *
 * <p>与 {@link TokenUsageAdvisor} 同样的纪律：只统计、不改写，异常不冒泡。
 */
@Slf4j
public class TokenAccountingEmbeddingModel implements EmbeddingModel {

  private final EmbeddingModel delegate;
  private final String providerId;
  private final LlmUsageSink sink;

  public TokenAccountingEmbeddingModel(EmbeddingModel delegate, String providerId, LlmUsageSink sink) {
    this.delegate = delegate;
    this.providerId = providerId;
    this.sink = sink;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    long startNanos = System.nanoTime();
    try {
      EmbeddingResponse response = delegate.call(request);
      reportSuccess(response, startNanos);
      return response;
    } catch (RuntimeException e) {
      reportFailure(startNanos);
      throw e;
    }
  }

  @Override
  public float[] embed(Document document) {
    long startNanos = System.nanoTime();
    try {
      float[] vector = delegate.embed(document);
      // embed(Document) 不返回 usage，成功时不记录（失败仍会记录），与 Chat 侧口径一致
      return vector;
    } catch (RuntimeException e) {
      reportFailure(startNanos);
      throw e;
    }
  }

  @Override
  public int dimensions() {
    // 直接委托，避免默认实现触发一次探测性 embedding 调用
    return delegate.dimensions();
  }

  private void reportSuccess(EmbeddingResponse response, long startNanos) {
    EmbeddingResponseMetadata metadata = response == null ? null : response.getMetadata();
    Usage usage = metadata == null ? null : metadata.getUsage();
    if (usage == null || safe(usage.getTotalTokens()) <= 0) {
      return;
    }
    emit(
        new LlmUsageSink.LlmUsage(
            providerId,
            metadata.getModel(),
            LlmUsageSink.KIND_EMBEDDING,
            LlmUsageSink.SOURCE_EMBEDDING,
            safe(usage.getPromptTokens()),
            safe(usage.getCompletionTokens()),
            safe(usage.getTotalTokens()),
            elapsedMs(startNanos),
            true));
  }

  private void reportFailure(long startNanos) {
    emit(
        LlmUsageSink.LlmUsage.failure(
            providerId, LlmUsageSink.KIND_EMBEDDING, LlmUsageSink.SOURCE_EMBEDDING, elapsedMs(startNanos)));
  }

  private void emit(LlmUsageSink.LlmUsage usage) {
    try {
      sink.record(usage);
    } catch (Exception e) {
      log.warn("[TokenUsage] Embedding 用量上报失败，已忽略: {}", e.toString());
    }
  }

  private static int safe(Integer value) {
    return value == null ? 0 : value;
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
