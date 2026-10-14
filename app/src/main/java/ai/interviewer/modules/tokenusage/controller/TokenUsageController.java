package ai.interviewer.modules.tokenusage.controller;

import ai.interviewer.common.annotation.RateLimit;
import ai.interviewer.common.result.Result;
import ai.interviewer.modules.tokenusage.dto.ModelPricingResponse;
import ai.interviewer.modules.tokenusage.dto.TokenUsageStatsResponse;
import ai.interviewer.modules.tokenusage.service.TokenUsageService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** LLM Token 用量与成本统计接口。 */
@RestController
@RequestMapping("/api/token-usage")
@RequiredArgsConstructor
@Slf4j
public class TokenUsageController {

  private final TokenUsageService tokenUsageService;

  /**
   * 查询用量统计。
   *
   * @param from 起始日期（含），ISO 格式 {@code yyyy-MM-dd}；缺省为近 7 天
   * @param to 结束日期（含）；缺省为今天
   * @param groupBy 分组维度：{@code model}（默认）/ {@code provider} / {@code kind} / {@code source}
   */
  @GetMapping("/stats")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<TokenUsageStatsResponse> stats(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String groupBy) {
    return Result.success(tokenUsageService.stats(from, to, groupBy));
  }

  /** 查询已配置的模型单价表。 */
  @GetMapping("/pricing")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<ModelPricingResponse> pricing() {
    return Result.success(tokenUsageService.pricing());
  }
}
