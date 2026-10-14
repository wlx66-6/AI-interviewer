package ai.interviewer.modules.tokenusage.repository;

import ai.interviewer.common.ai.LlmUsageSink;
import ai.interviewer.modules.tokenusage.dto.TokenUsageAggregate;
import ai.interviewer.modules.tokenusage.model.LlmTokenUsageEntity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聚合查询的 H2 集成测试。
 *
 * <p>这里验证的是 JPQL 本身——分组维度、时间窗过滤、失败计数用的 {@code case when} 表达式，
 * 这些在纯 Mockito 单测里覆盖不到，必须打到真实数据库才能确认。
 *
 * <p>说明两点：
 *
 * <ul>
 *   <li>Spring Boot 4 已不再提供 {@code @DataJpaTest} 切片（test-autoconfigure 被拆分），
 *       所以这里显式装配一个只含 DataSource + JPA 的最小上下文。
 *   <li>完整上下文会在启动时创建 Redisson 连接，本测试只关心 SQL，因此排除 Redisson 自动配置。
 * </ul>
 */
@SpringBootTest(classes = LlmTokenUsageRepositoryTest.JpaOnlyConfig.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("Token 用量聚合查询")
class LlmTokenUsageRepositoryTest {

  @EnableAutoConfiguration(
      exclude = {RedissonAutoConfigurationV2.class, RedissonAutoConfigurationV4.class})
  @EnableJpaRepositories(basePackageClasses = LlmTokenUsageRepository.class)
  @EntityScan(basePackageClasses = LlmTokenUsageEntity.class)
  static class JpaOnlyConfig {}

  private static final LocalDateTime INSIDE = LocalDateTime.of(2026, 5, 10, 12, 0);
  private static final LocalDateTime OUTSIDE = LocalDateTime.of(2026, 6, 1, 12, 0);

  @Autowired private LlmTokenUsageRepository repository;

  private static LlmTokenUsageEntity row(
      String model,
      String kind,
      String source,
      int prompt,
      int completion,
      long latencyMs,
      boolean success,
      LocalDateTime createdAt) {
    return LlmTokenUsageEntity.builder()
        .providerId("dashscope")
        .model(model)
        .kind(kind)
        .source(source)
        .promptTokens(prompt)
        .completionTokens(completion)
        .totalTokens(prompt + completion)
        .latencyMs(latencyMs)
        .success(success)
        .createdAt(createdAt)
        .build();
  }

  private List<TokenUsageAggregate> query(LocalDateTime from, LocalDateTime to) {
    return repository.aggregate(from, to);
  }

  @Test
  @DisplayName("按 provider/model/kind/source 四维分组并累加 Token、调用次数、平均时延与失败数")
  void aggregatesByFourDimensions() {
    repository.saveAll(
        List.of(
            row("qwen-plus", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 100, 50, 100, true, INSIDE),
            row("qwen-plus", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 300, 150, 300, false, INSIDE),
            row("qwen-plus", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_PLAIN, 10, 5, 20, true, INSIDE)));

    List<TokenUsageAggregate> result =
        query(INSIDE.minusDays(1), INSIDE.plusDays(1));

    assertThat(result).hasSize(2);

    TokenUsageAggregate merged =
        result.stream().filter(a -> LlmUsageSink.SOURCE_DEFAULT.equals(a.source())).findFirst().orElseThrow();
    assertThat(merged.providerId()).isEqualTo("dashscope");
    assertThat(merged.model()).isEqualTo("qwen-plus");
    assertThat(merged.kind()).isEqualTo(LlmUsageSink.KIND_CHAT);
    assertThat(merged.promptTokens()).isEqualTo(400L);
    assertThat(merged.completionTokens()).isEqualTo(200L);
    assertThat(merged.totalTokens()).isEqualTo(600L);
    assertThat(merged.calls()).isEqualTo(2L);
    assertThat(merged.failures()).isEqualTo(1L);
    assertThat(merged.avgLatencyMs()).isEqualTo(200.0);
  }

  @Test
  @DisplayName("时间窗按左闭右开过滤，窗外数据不参与聚合")
  void filtersByWindow() {
    repository.saveAll(
        List.of(
            row("qwen-plus", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 100, 0, 10, true, INSIDE),
            row("qwen-plus", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 999, 0, 10, true, OUTSIDE)));

    List<TokenUsageAggregate> result = query(INSIDE.minusDays(1), INSIDE.plusDays(1));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).totalTokens()).isEqualTo(100L);
  }

  @Test
  @DisplayName("窗口起点包含、终点排除")
  void windowIsHalfOpen() {
    LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
    LocalDateTime end = LocalDateTime.of(2026, 5, 2, 0, 0);
    repository.saveAll(
        List.of(
            row("m", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 1, 0, 1, true, start),
            row("m", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 2, 0, 1, true, end)));

    List<TokenUsageAggregate> result = query(start, end);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).totalTokens()).isEqualTo(1L);
  }

  @Test
  @DisplayName("结果按总 Token 倒序排列")
  void ordersByTotalTokensDesc() {
    repository.saveAll(
        List.of(
            row("small", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 10, 0, 1, true, INSIDE),
            row("big", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 900, 0, 1, true, INSIDE)));

    List<TokenUsageAggregate> result = query(INSIDE.minusDays(1), INSIDE.plusDays(1));

    assertThat(result).extracting(TokenUsageAggregate::model).containsExactly("big", "small");
  }

  @Test
  @DisplayName("Chat 与 Embedding 属于不同分组")
  void separatesKind() {
    repository.saveAll(
        List.of(
            row("qwen-plus", LlmUsageSink.KIND_CHAT, LlmUsageSink.SOURCE_DEFAULT, 100, 50, 10, true, INSIDE),
            row(
                "text-embedding-v3",
                LlmUsageSink.KIND_EMBEDDING,
                LlmUsageSink.SOURCE_EMBEDDING,
                200,
                0,
                20,
                true,
                INSIDE)));

    List<TokenUsageAggregate> result = query(INSIDE.minusDays(1), INSIDE.plusDays(1));

    assertThat(result).extracting(TokenUsageAggregate::kind)
        .containsExactlyInAnyOrder(LlmUsageSink.KIND_CHAT, LlmUsageSink.KIND_EMBEDDING);
  }

  @Test
  @DisplayName("空窗口返回空列表而不是 null")
  void emptyWindow() {
    assertThat(query(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(2000, 1, 2, 0, 0)))
        .isEmpty();
  }
}
