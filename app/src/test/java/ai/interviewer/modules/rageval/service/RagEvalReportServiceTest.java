package ai.interviewer.modules.rageval.service;

import ai.interviewer.common.exception.BusinessException;
import ai.interviewer.common.exception.ErrorCode;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDTO;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDetailDTO;
import ai.interviewer.modules.rageval.dto.RagEvalGroupMetricsDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;

/**
 * 直接读真实归档基线的集成测试。
 *
 * <p>不打整包归档文件内容的快照，只断言「结构可用」与「可区分的配置被正确解析」：
 * 指标数值会随重新评测而变，断言具体数值会让测试变成维护负担。
 */
@DisplayName("RAG 评测报告服务")
class RagEvalReportServiceTest {

  private RagEvalReportService service;

  @BeforeEach
  void setUp() {
    service =
        new RagEvalReportService(new ObjectMapper(), new PathMatchingResourcePatternResolver());
  }

  @Nested
  @DisplayName("基线列表")
  class ListBaselines {

    @Test
    @DisplayName("扫描到全部归档基线，并按评测时间升序返回")
    void loadsAllArchivedBaselines() {
      List<RagEvalBaselineDTO> baselines = service.list();

      assertThat(baselines).hasSize(6);
      assertThat(baselines)
          .extracting(RagEvalBaselineDTO::runId)
          .contains(
              "rag-eval-baseline-1788403462034",
              "rag-eval-merge-1788405609214",
              "rag-eval-rewrite-only-1788407548196",
              "rag-eval-chunk-400-1788426865814",
              "rag-eval-chunk-1200-1788427396556",
              "rag-eval-chunk-800-1788427955758");
      assertThat(baselines).extracting(RagEvalBaselineDTO::timestamp).isSorted();
    }

    @Test
    @DisplayName("每组基线都有总体指标与完整的 40 条样本口径")
    void everyBaselineIsUsable() {
      for (RagEvalBaselineDTO baseline : service.list()) {
        assertThat(baseline.overall()).as(baseline.runId()).isNotNull();
        assertThat(baseline.overall().samples()).as(baseline.runId()).isEqualTo(40);
        assertThat(baseline.rejection()).as(baseline.runId()).isNotNull();
        assertThat(baseline.label()).as(baseline.runId()).isNotBlank();
      }
    }

    @Test
    @DisplayName("三组分块对照的分块大小被正确解析，且互不相同")
    void parsesChunkSizeOfContrastRuns() {
      assertThat(chunkSizeOf("rag-eval-chunk-400-1788426865814")).isEqualTo(400);
      assertThat(chunkSizeOf("rag-eval-chunk-800-1788427955758")).isEqualTo(800);
      assertThat(chunkSizeOf("rag-eval-chunk-1200-1788427396556")).isEqualTo(1200);
    }

    @Test
    @DisplayName("开关类配置从『值 + 说明』的字符串里解析，缺失时保持 null")
    void parsesSwitchFlags() {
      RagEvalBaselineDTO merge = find("rag-eval-merge-1788405609214");
      assertThat(merge.rewriteEnabled()).isTrue();
      assertThat(merge.mergeOriginalQuery()).isTrue();
      assertThat(merge.chunkSize()).isNull();

      RagEvalBaselineDTO rewriteOnly = find("rag-eval-rewrite-only-1788407548196");
      assertThat(rewriteOnly.rewriteEnabled()).isTrue();
      assertThat(rewriteOnly.mergeOriginalQuery()).isFalse();

      RagEvalBaselineDTO baseline = find("rag-eval-baseline-1788403462034");
      assertThat(baseline.rewriteEnabled()).isFalse();
      // 旧版报告没有记录该开关，应为 null（未记录）而不是 false（已关闭）
      assertThat(baseline.mergeOriginalQuery()).isNull();
    }

    @Test
    @DisplayName("列表解析结果被缓存，重复调用不再扫描 classpath")
    void cachesSummaries() {
      assertThat(service.list()).isSameAs(service.list());
    }
  }

  @Nested
  @DisplayName("单次运行详情")
  class RunDetail {

    @Test
    @DisplayName("详情包含切分、题型分组与逐样本明细")
    void exposesGroupsAndSamples() {
      RagEvalBaselineDetailDTO detail = service.detail("rag-eval-chunk-800-1788427955758");

      assertThat(detail.summary().label()).isEqualTo("Chunk 800");
      assertThat(detail.samples()).hasSize(40);
      assertThat(detail.groups())
          .extracting(RagEvalGroupMetricsDTO::group)
          .contains("dev", "holdout", "直接事实题", "知识库外题");
      assertThat(detail.markdownAvailable()).isTrue();
      assertThat(detail.environment().model()).isEqualTo("qwen3.5-flash");
      assertThat(detail.badCases()).hasSize(detail.summary().badCaseCount());
    }

    @Test
    @DisplayName("重复查询同一运行返回同一份缓存对象")
    void cachesDetail() {
      RagEvalBaselineDetailDTO first = service.detail("rag-eval-chunk-400-1788426865814");
      assertThat(service.detail("rag-eval-chunk-400-1788426865814")).isSameAs(first);
    }

    @Test
    @DisplayName("不存在的运行抛出 NOT_FOUND 业务异常")
    void unknownRunIsNotFound() {
      assertThatThrownBy(() -> service.detail("rag-eval-not-exist-1"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              e ->
                  assertThat(((BusinessException) e).getCode())
                      .isEqualTo(ErrorCode.RAG_EVAL_BASELINE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("带路径分隔符的 runId 被拒绝，不会越出基线目录")
    void rejectsPathTraversalRunId() {
      assertThatThrownBy(() -> service.detail("../../application"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              e ->
                  assertThat(((BusinessException) e).getCode())
                      .isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
      assertThatThrownBy(() -> service.detail(".."))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              e ->
                  assertThat(((BusinessException) e).getCode())
                      .isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }
  }

  @Nested
  @DisplayName("Markdown 原报告")
  class MarkdownReport {

    @Test
    @DisplayName("返回归档的 Markdown 原文")
    void returnsArchivedMarkdown() {
      String markdown = service.markdown("rag-eval-chunk-800-1788427955758");

      assertThat(markdown).startsWith("# RAG 测评报告：rag-eval-chunk-800-1788427955758");
      assertThat(markdown).contains("## 拒答混淆矩阵");
    }

    @Test
    @DisplayName("没有归档 Markdown 的运行抛出 NOT_FOUND")
    void missingMarkdownIsNotFound() {
      assertThatThrownBy(() -> service.markdown("rag-eval-not-exist-1"))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              e ->
                  assertThat(((BusinessException) e).getCode())
                      .isEqualTo(ErrorCode.RAG_EVAL_BASELINE_NOT_FOUND.getCode()));
    }
  }

  private RagEvalBaselineDTO find(String runId) {
    return service.list().stream()
        .filter(baseline -> runId.equals(baseline.runId()))
        .findFirst()
        .orElseThrow();
  }

  private Integer chunkSizeOf(String runId) {
    return find(runId).chunkSize();
  }
}
