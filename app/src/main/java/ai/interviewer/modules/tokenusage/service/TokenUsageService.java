package ai.interviewer.modules.tokenusage.service;

import ai.interviewer.common.ai.LlmUsageSink;
import ai.interviewer.common.config.LlmPricingProperties;
import ai.interviewer.common.exception.BusinessException;
import ai.interviewer.common.exception.ErrorCode;
import ai.interviewer.modules.tokenusage.dto.ModelPriceDTO;
import ai.interviewer.modules.tokenusage.dto.ModelPricingResponse;
import ai.interviewer.modules.tokenusage.dto.TokenUsageAggregate;
import ai.interviewer.modules.tokenusage.dto.TokenUsageRowDTO;
import ai.interviewer.modules.tokenusage.dto.TokenUsageStatsResponse;
import ai.interviewer.modules.tokenusage.repository.LlmTokenUsageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Token 用量与成本查询。
 *
 * <p>聚合在数据库内完成（见 {@link LlmTokenUsageRepository#aggregate}），本类只做分组、计价与汇总。
 */
@Service
@RequiredArgsConstructor
public class TokenUsageService {

  /** 未显式指定时间窗时默认统计近 7 天（含今天）。 */
  private static final int DEFAULT_WINDOW_DAYS = 7;

  private static final BigDecimal PER_THOUSAND = BigDecimal.valueOf(1000);
  private static final int COST_SCALE = 6;
  private static final String UNKNOWN_LABEL = "未指定";

  private final LlmTokenUsageRepository repository;
  private final LlmPricingProperties pricingProperties;

  /** 查询用量统计。 */
  @Transactional(readOnly = true)
  public TokenUsageStatsResponse stats(LocalDate fromParam, LocalDate toParam, String groupByParam) {
    LocalDate to = toParam != null ? toParam : LocalDate.now();
    LocalDate from = fromParam != null ? fromParam : to.minusDays(DEFAULT_WINDOW_DAYS - 1L);
    if (from.isAfter(to)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
    }
    Dimension dimension = Dimension.parse(groupByParam);

    // 结束日按「当天 24:00」开区间处理，避免漏掉当天数据
    List<TokenUsageAggregate> aggregates =
        repository.aggregate(from.atStartOfDay(), to.plusDays(1).atStartOfDay());

    Map<String, RowAccumulator> grouped = new LinkedHashMap<>();
    for (TokenUsageAggregate aggregate : aggregates) {
      String key = dimension.keyOf(aggregate);
      grouped
          .computeIfAbsent(key, ignored -> new RowAccumulator(key, aggregate))
          .add(aggregate, costOf(aggregate));
    }

    List<TokenUsageRowDTO> rows = new ArrayList<>();
    long promptTokens = 0;
    long completionTokens = 0;
    long totalTokens = 0;
    long calls = 0;
    long failures = 0;
    BigDecimal totalCost = BigDecimal.ZERO;
    boolean costComplete = true;

    for (RowAccumulator accumulator : grouped.values()) {
      TokenUsageRowDTO row = accumulator.toRow();
      rows.add(row);
      promptTokens += row.promptTokens();
      completionTokens += row.completionTokens();
      totalTokens += row.totalTokens();
      calls += row.calls();
      failures += row.failures();
      if (row.priced()) {
        totalCost = totalCost.add(row.cost());
      } else {
        costComplete = false;
      }
    }

    rows.sort(Comparator.comparingLong(TokenUsageRowDTO::totalTokens).reversed());

    return new TokenUsageStatsResponse(
        from,
        to,
        dimension.name().toLowerCase(Locale.ROOT),
        pricingProperties.getCurrency(),
        promptTokens,
        completionTokens,
        totalTokens,
        calls,
        failures,
        totalCost,
        costComplete,
        rows);
  }

  /** 返回已配置的单价表，便于前端解释金额口径。 */
  @Transactional(readOnly = true)
  public ModelPricingResponse pricing() {
    List<ModelPriceDTO> models =
        pricingProperties.getModels().entrySet().stream()
            .map(
                entry ->
                    new ModelPriceDTO(
                        entry.getKey(),
                        entry.getValue().getInputPer1k(),
                        entry.getValue().getOutputPer1k()))
            .toList();
    return new ModelPricingResponse(pricingProperties.getCurrency(), models);
  }

  /**
   * 按单价换算一组聚合结果的金额。
   *
   * <p>返回空表示该模型未配置单价，或缺少该调用类别必需的单价——此时金额是「未知」，不能当成 0。
   */
  private Optional<BigDecimal> costOf(TokenUsageAggregate aggregate) {
    Optional<LlmPricingProperties.ModelPrice> priceOpt = pricingProperties.find(aggregate.model());
    if (priceOpt.isEmpty()) {
      return Optional.empty();
    }
    LlmPricingProperties.ModelPrice price = priceOpt.get();
    boolean embedding = LlmUsageSink.KIND_EMBEDDING.equals(aggregate.kind());

    if (price.getInputPer1k() == null || (!embedding && price.getOutputPer1k() == null)) {
      return Optional.empty();
    }

    BigDecimal inputCost =
        BigDecimal.valueOf(price.getInputPer1k())
            .multiply(BigDecimal.valueOf(nullToZero(aggregate.promptTokens())))
            .divide(PER_THOUSAND, COST_SCALE, RoundingMode.HALF_UP);

    BigDecimal outputCost = BigDecimal.ZERO;
    if (!embedding) {
      outputCost =
          BigDecimal.valueOf(price.getOutputPer1k())
              .multiply(BigDecimal.valueOf(nullToZero(aggregate.completionTokens())))
              .divide(PER_THOUSAND, COST_SCALE, RoundingMode.HALF_UP);
    }
    return Optional.of(inputCost.add(outputCost));
  }

  private static long nullToZero(Long value) {
    return value == null ? 0L : value;
  }

  /** 可用的分组维度。 */
  private enum Dimension {
    MODEL,
    PROVIDER,
    KIND,
    SOURCE;

    static Dimension parse(String raw) {
      if (raw == null || raw.isBlank()) {
        return MODEL;
      }
      try {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的 groupBy: " + raw);
      }
    }

    /** 取该维度的分组键；字段为空时归入「未指定」，保证历史脏数据也能被统计到。 */
    String keyOf(TokenUsageAggregate aggregate) {
      String value =
          switch (this) {
            case MODEL -> aggregate.model();
            case PROVIDER -> aggregate.providerId();
            case KIND -> aggregate.kind();
            case SOURCE -> aggregate.source();
          };
      return value == null || value.isBlank() ? UNKNOWN_LABEL : value;
    }
  }

  /** 单个分组的累加器。 */
  private static final class RowAccumulator {

    private final String key;
    private final String providerId;
    private final String model;
    private final String kind;
    private final String source;

    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long calls;
    private long failures;
    private double latencyWeightedSum;
    private BigDecimal cost = BigDecimal.ZERO;
    private boolean priced = true;

    RowAccumulator(String key, TokenUsageAggregate first) {
      this.key = key;
      this.providerId = first.providerId();
      this.model = first.model();
      this.kind = first.kind();
      this.source = first.source();
    }

    void add(TokenUsageAggregate aggregate, Optional<BigDecimal> aggregateCost) {
      promptTokens += nullToZero(aggregate.promptTokens());
      completionTokens += nullToZero(aggregate.completionTokens());
      totalTokens += nullToZero(aggregate.totalTokens());
      long aggregateCalls = nullToZero(aggregate.calls());
      calls += aggregateCalls;
      failures += nullToZero(aggregate.failures());
      if (aggregate.avgLatencyMs() != null) {
        latencyWeightedSum += aggregate.avgLatencyMs() * aggregateCalls;
      }
      if (aggregateCost.isPresent()) {
        cost = cost.add(aggregateCost.get());
      } else {
        priced = false;
      }
    }

    TokenUsageRowDTO toRow() {
      long avgLatency = calls > 0 ? Math.round(latencyWeightedSum / calls) : 0L;
      return new TokenUsageRowDTO(
          key,
          providerId,
          model,
          kind,
          source,
          promptTokens,
          completionTokens,
          totalTokens,
          calls,
          failures,
          avgLatency,
          priced ? cost : null,
          priced);
    }
  }

  }
