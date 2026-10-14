package ai.interviewer.modules.tokenusage.service;

import ai.interviewer.common.ai.LlmUsageSink;
import ai.interviewer.common.config.LlmPricingProperties;
import ai.interviewer.common.exception.BusinessException;
import ai.interviewer.modules.tokenusage.dto.ModelPricingResponse;
import ai.interviewer.modules.tokenusage.dto.TokenUsageAggregate;
import ai.interviewer.modules.tokenusage.dto.TokenUsageRowDTO;
import ai.interviewer.modules.tokenusage.dto.TokenUsageStatsResponse;
import ai.interviewer.modules.tokenusage.repository.LlmTokenUsageRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link TokenUsageService} 的分组、计价与参数校验测试（不依赖数据库）。 */
class TokenUsageServiceTest {

  private LlmTokenUsageRepository repository;
  private LlmPricingProperties pricing;
  private TokenUsageService service;

  @BeforeEach
  void setUp() {
    repository = mock(LlmTokenUsageRepository.class);
    pricing = new LlmPricingProperties();
    service = new TokenUsageService(repository, pricing);
  }

  private static TokenUsageAggregate chat(
      String model, long prompt, long completion, long calls, double avgLatency, long failures) {
    return new TokenUsageAggregate(
        "dashscope",
        model,
        LlmUsageSink.KIND_CHAT,
        LlmUsageSink.SOURCE_DEFAULT,
        prompt,
        completion,
        prompt + completion,
        calls,
        avgLatency,
        failures);
  }

  private void price(String model, Double inputPer1k, Double outputPer1k) {
    LlmPricingProperties.ModelPrice modelPrice = new LlmPricingProperties.ModelPrice();
    modelPrice.setInputPer1k(inputPer1k);
    modelPrice.setOutputPer1k(outputPer1k);
    pricing.getModels().put(model, modelPrice);
  }

  @Nested
  @DisplayName("时间窗")
  class TimeWindow {

    @Test
    @DisplayName("不传日期时默认统计近 7 天（含今天）")
    void defaultWindow() {
      when(repository.aggregate(any(), any())).thenReturn(List.of());

      TokenUsageStatsResponse stats = service.stats(null, null, null);

      LocalDate today = LocalDate.now();
      assertThat(stats.to()).isEqualTo(today);
      assertThat(stats.from()).isEqualTo(today.minusDays(6));
      assertThat(stats.groupBy()).isEqualTo("model");
    }

    @Test
    @DisplayName("结束日按次日 0 点开区间查询，避免漏掉当天数据")
    void endIsExclusiveNextDay() {
      when(repository.aggregate(any(), any())).thenReturn(List.of());

      service.stats(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), "provider");

      ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
      verify(repository).aggregate(captor.capture(), captor.capture());
      assertThat(captor.getAllValues())
          .containsExactly(
              LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 4, 0, 0));
    }

    @Test
    @DisplayName("起始日晚于结束日时抛出业务异常")
    void invalidRange() {
      assertThatThrownBy(() -> service.stats(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  @DisplayName("分组维度")
  class Grouping {

    @Test
    @DisplayName("不支持的 groupBy 抛出业务异常")
    void unknownGroupBy() {
      assertThatThrownBy(() -> service.stats(null, null, "model-name"))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("同一模型的多条聚合合并为一行，并按调用次数加权平均时延")
    void mergesSameModel() {
      when(repository.aggregate(any(), any()))
          .thenReturn(
              List.of(
                  chat("qwen-plus", 100, 50, 1, 100, 0),
                  chat("qwen-plus", 300, 150, 3, 300, 1)));

      TokenUsageStatsResponse stats = service.stats(null, null, "model");

      assertThat(stats.rows()).hasSize(1);
      TokenUsageRowDTO row = stats.rows().get(0);
      assertThat(row.key()).isEqualTo("qwen-plus");
      assertThat(row.promptTokens()).isEqualTo(400);
      assertThat(row.completionTokens()).isEqualTo(200);
      assertThat(row.totalTokens()).isEqualTo(600);
      assertThat(row.calls()).isEqualTo(4);
      assertThat(row.failures()).isEqualTo(1);
      // (100*1 + 300*3) / 4 = 250
      assertThat(row.avgLatencyMs()).isEqualTo(250);
      assertThat(stats.totalTokens()).isEqualTo(600);
      assertThat(stats.calls()).isEqualTo(4);
    }

    @Test
    @DisplayName("按 kind 分组时把 CHAT 与 EMBEDDING 分成两行")
    void groupsByKind() {
      when(repository.aggregate(any(), any()))
          .thenReturn(
              List.of(
                  chat("qwen-plus", 100, 50, 1, 10, 0),
                  new TokenUsageAggregate(
                      "dashscope",
                      "text-embedding-v3",
                      LlmUsageSink.KIND_EMBEDDING,
                      LlmUsageSink.SOURCE_EMBEDDING,
                      200L,
                      0L,
                      200L,
                      2L,
                      20.0,
                      0L)));

      TokenUsageStatsResponse stats = service.stats(null, null, "kind");

      assertThat(stats.rows()).extracting(TokenUsageRowDTO::key)
          .containsExactlyInAnyOrder(LlmUsageSink.KIND_CHAT, LlmUsageSink.KIND_EMBEDDING);
    }

    @Test
    @DisplayName("明细行按总 Token 倒序排列")
    void rowsSortedByTotalTokens() {
      when(repository.aggregate(any(), any()))
          .thenReturn(List.of(chat("small", 10, 10, 1, 5, 0), chat("big", 900, 900, 1, 5, 0)));

      TokenUsageStatsResponse stats = service.stats(null, null, "model");

      assertThat(stats.rows()).extracting(TokenUsageRowDTO::key).containsExactly("big", "small");
    }
  }

  @Nested
  @DisplayName("成本换算")
  class Costing {

    @Test
    @DisplayName("配置单价后按「每千 Token」折算金额")
    void computesCost() {
      price("qwen-plus", 0.004, 0.012);
      when(repository.aggregate(any(), any()))
          .thenReturn(List.of(chat("qwen-plus", 1000, 500, 1, 10, 0)));

      TokenUsageStatsResponse stats = service.stats(null, null, "model");

      // 1000/1000*0.004 + 500/1000*0.012 = 0.010
      assertThat(stats.rows().get(0).cost()).isEqualByComparingTo("0.010");
      assertThat(stats.rows().get(0).priced()).isTrue();
      assertThat(stats.costComplete()).isTrue();
      assertThat(stats.totalCost()).isEqualByComparingTo("0.010");
      assertThat(stats.currency()).isEqualTo("CNY");
    }

    @Test
    @DisplayName("未配置单价的模型成本返回 null，而不是 0")
    void unpricedModelReturnsNullCost() {
      when(repository.aggregate(any(), any()))
          .thenReturn(List.of(chat("unknown-model", 1000, 500, 1, 10, 0)));

      TokenUsageStatsResponse stats = service.stats(null, null, "model");

      TokenUsageRowDTO row = stats.rows().get(0);
      assertThat(row.cost()).isNull();
      assertThat(row.priced()).isFalse();
      assertThat(stats.costComplete()).isFalse();
      // 已知部分仍求和，但用 costComplete=false 明确标注「金额不完整」
      assertThat(stats.totalCost()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("部分模型有单价时，汇总金额只累计可计价部分并标记不完整")
    void partialPricingMarksIncomplete() {
      price("qwen-plus", 0.004, 0.012);
      when(repository.aggregate(any(), any()))
          .thenReturn(
              List.of(
                  chat("qwen-plus", 1000, 0, 1, 10, 0),
                  chat("unknown-model", 9999, 9999, 1, 10, 0)));

      TokenUsageStatsResponse stats = service.stats(null, null, "model");

      assertThat(stats.totalCost()).isEqualByComparingTo("0.004");
      assertThat(stats.costComplete()).isFalse();
    }

    @Test
    @DisplayName("对话模型缺少输出单价时不计价；Embedding 只要求输入单价")
    void requiresPricePerKind() {
      price("chat-only-input", 0.004, null);
      price("embedding-only-input", 0.0005, null);
      when(repository.aggregate(any(), any()))
          .thenReturn(
              List.of(
                  chat("chat-only-input", 1000, 500, 1, 10, 0),
                  new TokenUsageAggregate(
                      "dashscope",
                      "embedding-only-input",
                      LlmUsageSink.KIND_EMBEDDING,
                      LlmUsageSink.SOURCE_EMBEDDING,
                      1000L,
                      0L,
                      1000L,
                      1L,
                      5.0,
                      0L)));

      TokenUsageStatsResponse stats = service.stats(null, null, "model");

      TokenUsageRowDTO chatRow =
          stats.rows().stream().filter(r -> r.model().equals("chat-only-input")).findFirst().orElseThrow();
      TokenUsageRowDTO embeddingRow =
          stats.rows().stream()
              .filter(r -> r.model().equals("embedding-only-input"))
              .findFirst()
              .orElseThrow();

      assertThat(chatRow.priced()).isFalse();
      assertThat(embeddingRow.priced()).isTrue();
      assertThat(embeddingRow.cost()).isEqualByComparingTo("0.0005");
    }
  }

  @Nested
  @DisplayName("单价表接口")
  class Pricing {

    @Test
    @DisplayName("返回已配置的模型与币种")
    void returnsConfiguredModels() {
      price("qwen-plus", 0.004, 0.012);
      pricing.setCurrency("USD");

      ModelPricingResponse response = service.pricing();

      assertThat(response.currency()).isEqualTo("USD");
      assertThat(response.models()).hasSize(1);
      assertThat(response.models().get(0).model()).isEqualTo("qwen-plus");
      assertThat(response.models().get(0).inputPer1k()).isEqualTo(0.004);
    }
  }
}
