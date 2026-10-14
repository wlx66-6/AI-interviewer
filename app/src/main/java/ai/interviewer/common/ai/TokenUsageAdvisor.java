package ai.interviewer.common.ai;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 拦截单次 Chat 调用并上报 Token 用量与时延。
 *
 * <p>只统计、不改写请求与响应：{@code adviseCall} / {@code adviseStream} 原样返回下游结果，任何统计异常都被
 * 吞掉，因此 <b>不会影响 LLM 主链路</b>。
 *
 * <p>两个实现细节：
 *
 * <ul>
 *   <li>流式调用中每一块都可能带 usage，这里只取最后一块非零 usage，避免重复计数。
 *   <li>上游未返回 usage（例如空 usage）时<b>不记录</b>——宁可缺失，也不写入 0 值污染统计口径；
 *       但调用失败一定会记一条 {@code success=false}，用于统计错误率。
 * </ul>
 *
 * <p>顺序约定：{@link #getOrder()} 默认 0，即最外层，先于安全护栏（100）等 Advisor 执行。
 */
@Slf4j
public class TokenUsageAdvisor implements CallAdvisor, StreamAdvisor {

  private static final String NAME = "TokenUsageAdvisor";

  private final String providerId;
  private final String source;
  private final LlmUsageSink sink;
  private final int order;

  public TokenUsageAdvisor(String providerId, String source, LlmUsageSink sink) {
    this(providerId, source, sink, 0);
  }

  public TokenUsageAdvisor(String providerId, String source, LlmUsageSink sink, int order) {
    this.providerId = providerId;
    this.source = source;
    this.sink = sink;
    this.order = order;
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    long startNanos = System.nanoTime();
    try {
      ChatClientResponse response = chain.nextCall(request);
      report(response, startNanos);
      return response;
    } catch (RuntimeException e) {
      reportFailure(startNanos);
      throw e;
    }
  }

  @Override
  public Flux<ChatClientResponse> adviseStream(
      ChatClientRequest request, StreamAdvisorChain chain) {
    long startNanos = System.nanoTime();
    // 流式响应会被订阅/消费一次，用 AtomicBoolean 保证「成功」与「失败」只上报一次
    AtomicBoolean reported = new AtomicBoolean(false);
    AtomicReference<ChatClientResponse> lastWithUsage = new AtomicReference<>();

    return chain
        .nextStream(request)
        .doOnNext(
            response -> {
              if (hasUsage(response)) {
                lastWithUsage.set(response);
              }
            })
        .doOnComplete(
            () -> {
              if (reported.compareAndSet(false, true)) {
                report(lastWithUsage.get(), startNanos);
              }
            })
        .doOnError(
            error -> {
              if (reported.compareAndSet(false, true)) {
                reportFailure(startNanos);
              }
            });
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getOrder() {
    return order;
  }

  private void report(ChatClientResponse response, long startNanos) {
    Usage usage = extractUsage(response);
    if (usage == null || safe(usage.getTotalTokens()) <= 0) {
      // 上游没有给出 usage：不写 0 值行，保持统计口径可信
      return;
    }
    emit(
        new LlmUsageSink.LlmUsage(
            providerId,
            extractModel(response),
            LlmUsageSink.KIND_CHAT,
            source,
            safe(usage.getPromptTokens()),
            safe(usage.getCompletionTokens()),
            safe(usage.getTotalTokens()),
            elapsedMs(startNanos),
            true));
  }

  private void reportFailure(long startNanos) {
    emit(LlmUsageSink.LlmUsage.failure(providerId, LlmUsageSink.KIND_CHAT, source, elapsedMs(startNanos)));
  }

  private void emit(LlmUsageSink.LlmUsage usage) {
    try {
      sink.record(usage);
    } catch (Exception e) {
      // 旁路能力：上报失败只告警，绝不冒泡到调用方
      log.warn("[TokenUsageAdvisor] 用量上报失败，已忽略: {}", e.toString());
    }
  }

  private static boolean hasUsage(ChatClientResponse response) {
    Usage usage = extractUsage(response);
    return usage != null && safe(usage.getTotalTokens()) > 0;
  }

  private static Usage extractUsage(ChatClientResponse response) {
    if (response == null || response.chatResponse() == null) {
      return null;
    }
    ChatResponseMetadata metadata = response.chatResponse().getMetadata();
    return metadata == null ? null : metadata.getUsage();
  }

  private static String extractModel(ChatClientResponse response) {
    ChatResponseMetadata metadata = response.chatResponse().getMetadata();
    return metadata == null ? null : metadata.getModel();
  }

  private static int safe(Integer value) {
    return value == null ? 0 : value;
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
