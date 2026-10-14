package ai.interviewer.modules.rageval.controller;

import ai.interviewer.common.annotation.RateLimit;
import ai.interviewer.common.result.Result;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDTO;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDetailDTO;
import ai.interviewer.modules.rageval.service.RagEvalReportService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** RAG 检索质量评测报告接口：读取归档基线，不触发任何模型调用。 */
@RestController
@RequestMapping("/api/rag-eval")
@RequiredArgsConstructor
@Slf4j
public class RagEvalController {

  private final RagEvalReportService ragEvalReportService;

  /** 已归档的评测基线列表，按评测时间升序。 */
  @GetMapping("/baselines")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<List<RagEvalBaselineDTO>> baselines() {
    return Result.success(ragEvalReportService.list());
  }

  /** 单次运行的完整报告：分组指标、拒答矩阵、坏例与逐样本明细。 */
  @GetMapping("/baselines/{runId}")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 60)
  public Result<RagEvalBaselineDetailDTO> baseline(@PathVariable String runId) {
    return Result.success(ragEvalReportService.detail(runId));
  }

  /** 归档的 Markdown 原报告。 */
  @GetMapping("/baselines/{runId}/markdown")
  @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
  public Result<String> markdown(@PathVariable String runId) {
    return Result.success(ragEvalReportService.markdown(runId));
  }
}
