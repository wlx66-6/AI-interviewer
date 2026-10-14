package ai.interviewer.modules.tokenusage.service;

import ai.interviewer.common.ai.LlmUsageSink;
import ai.interviewer.common.transaction.TransactionalExecutor;
import ai.interviewer.modules.tokenusage.model.LlmTokenUsageEntity;
import ai.interviewer.modules.tokenusage.repository.LlmTokenUsageRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link TokenUsageRecorder} 的落库映射与容错测试。 */
class TokenUsageRecorderTest {

  private LlmTokenUsageRepository repository;
  private TokenUsageRecorder recorder;

  @BeforeEach
  void setUp() {
    repository = mock(LlmTokenUsageRepository.class);
    recorder = new TokenUsageRecorder(repository, new TransactionalExecutor());
  }

  private static LlmUsageSink.LlmUsage usage(String providerId, String model) {
    return new LlmUsageSink.LlmUsage(
        providerId,
        model,
        LlmUsageSink.KIND_CHAT,
        LlmUsageSink.SOURCE_DEFAULT,
        120,
        80,
        200,
        350L,
        true);
  }

  @Test
  @DisplayName("按用量快照写入完整字段")
  void mapsAllFields() {
    recorder.record(usage("dashscope", "qwen-plus"));

    ArgumentCaptor<LlmTokenUsageEntity> captor = ArgumentCaptor.forClass(LlmTokenUsageEntity.class);
    verify(repository).save(captor.capture());
    LlmTokenUsageEntity entity = captor.getValue();
    assertThat(entity.getProviderId()).isEqualTo("dashscope");
    assertThat(entity.getModel()).isEqualTo("qwen-plus");
    assertThat(entity.getKind()).isEqualTo(LlmUsageSink.KIND_CHAT);
    assertThat(entity.getSource()).isEqualTo(LlmUsageSink.SOURCE_DEFAULT);
    assertThat(entity.getPromptTokens()).isEqualTo(120);
    assertThat(entity.getCompletionTokens()).isEqualTo(80);
    assertThat(entity.getTotalTokens()).isEqualTo(200);
    assertThat(entity.getLatencyMs()).isEqualTo(350L);
    assertThat(entity.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("provider 为空时落到 default，避免非空约束报错")
  void fallsBackToDefaultProvider() {
    recorder.record(usage(null, "qwen-plus"));
    recorder.record(usage("   ", "qwen-plus"));

    ArgumentCaptor<LlmTokenUsageEntity> captor = ArgumentCaptor.forClass(LlmTokenUsageEntity.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).extracting(LlmTokenUsageEntity::getProviderId)
        .containsOnly("default");
  }

  @Test
  @DisplayName("超长字段按列宽截断，避免写入失败")
  void truncatesOverlongFields() {
    recorder.record(usage("p".repeat(100), "m".repeat(200)));

    ArgumentCaptor<LlmTokenUsageEntity> captor = ArgumentCaptor.forClass(LlmTokenUsageEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getProviderId()).hasSize(64);
    assertThat(captor.getValue().getModel()).hasSize(128);
  }

  @Test
  @DisplayName("落库失败被吞掉，不向 LLM 主链路抛异常")
  void writeFailureIsSwallowed() {
    when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

    assertThatCode(() -> recorder.record(usage("dashscope", "qwen-plus")))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("负数 Token 归零，避免脏数据进统计")
  void clampsNegativeTokens() {
    recorder.record(
        new LlmUsageSink.LlmUsage(
            "dashscope",
            "qwen-plus",
            LlmUsageSink.KIND_CHAT,
            LlmUsageSink.SOURCE_DEFAULT,
            -1,
            -2,
            -3,
            10L,
            true));

    ArgumentCaptor<LlmTokenUsageEntity> captor = ArgumentCaptor.forClass(LlmTokenUsageEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getPromptTokens()).isZero();
    assertThat(captor.getValue().getCompletionTokens()).isZero();
    assertThat(captor.getValue().getTotalTokens()).isZero();
  }
}
