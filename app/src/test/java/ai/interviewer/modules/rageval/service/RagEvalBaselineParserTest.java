package ai.interviewer.modules.rageval.service;

import ai.interviewer.modules.rageval.dto.RagEvalBaselineDTO;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDetailDTO;
import ai.interviewer.modules.rageval.dto.RagEvalGroupMetricsDTO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 归档报告 JSON → DTO 的映射单元测试。
 *
 * <p>样本数据照着真实归档文件（{@code rag-eval-chunk-800-*})的字段名构造，重点覆盖两件事：
 * 归档文件来自不同版本的评估器，字段可能缺失；环境快照里的值是「值 + 说明」的字符串。
 */
@DisplayName("RAG 评测基线解析")
class RagEvalBaselineParserTest {

  @Nested
  @DisplayName("概要映射")
  class Summary {

    @Test
    @DisplayName("头信息、总体指标与拒答矩阵映射到列表项")
    void mapsSummaryFields() {
      RagEvalBaselineDTO summary = RagEvalBaselineParser.toSummary(archive());

      assertThat(summary.runId()).isEqualTo("rag-eval-chunk-800-1788427955758");
      assertThat(summary.label()).isEqualTo("Chunk 800");
      assertThat(summary.timestamp()).isEqualTo("2026-09-03T09:40:08.131821Z");
      assertThat(summary.datasetVersion()).isEqualTo("rag-eval/dataset-v1.jsonl");
      assertThat(summary.datasetSize()).isEqualTo(40);
      assertThat(summary.overall().samples()).isEqualTo(40);
      assertThat(summary.overall().inScope()).isEqualTo(32);
      assertThat(summary.overall().hitAtK()).isEqualTo(87.5);
      assertThat(summary.overall().evidenceRecallAtK()).isEqualTo(0.8281);
      assertThat(summary.overall().retrievalMsP50()).isEqualTo(665L);
      assertThat(summary.rejection().fn()).isEqualTo(8);
      assertThat(summary.rejection().samples()).isEqualTo(18);
      assertThat(summary.badCaseCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("环境快照里带说明文字的值只提取可判断的部分")
    void parsesAnnotatedEnvironmentValues() {
      RagEvalBaselineDTO summary = RagEvalBaselineParser.toSummary(archive());

      assertThat(summary.chunkSize()).isEqualTo(800);
      assertThat(summary.rewriteEnabled()).isFalse();
      assertThat(summary.mergeOriginalQuery()).isFalse();
    }

    @Test
    @DisplayName("缺失字段映射为 null，不伪造 0 或 false")
    void keepsMissingFieldsNull() {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("runId", "rag-eval-baseline-1788403462034");
      root.put("environment", Map.of());
      root.put("metrics", Map.of());
      root.put("badCases", List.of());

      RagEvalBaselineDTO summary = RagEvalBaselineParser.toSummary(root);

      assertThat(summary.overall()).isNull();
      assertThat(summary.rejection()).isNull();
      assertThat(summary.evaluatorVersion()).isNull();
      assertThat(summary.chunkSize()).isNull();
      assertThat(summary.rewriteEnabled()).isNull();
      assertThat(summary.badCaseCount()).isZero();
    }

    @Test
    @DisplayName("配置名由 runId 推导，未知配置回落为『基线配置』")
    void derivesLabelFromRunId() {
      assertThat(labelOf("rag-eval-chunk-1200-1788427396556")).isEqualTo("Chunk 1200");
      assertThat(labelOf("rag-eval-rewrite-only-1788407548196")).isEqualTo("仅开启改写");
      assertThat(labelOf("rag-eval-merge-1788405609214")).isEqualTo("改写 + 双路融合");
      assertThat(labelOf("rag-eval-baseline-1788403462034")).isEqualTo("基线配置");
    }
  }

  @Nested
  @DisplayName("详情映射")
  class Detail {

    @Test
    @DisplayName("按切分与题型分组，无 inScope 的题型组仍保留耗时指标")
    void buildsGroups() {
      RagEvalBaselineDetailDTO detail =
          RagEvalBaselineParser.toDetail(archive(), true);

      assertThat(detail.groups())
          .extracting(RagEvalGroupMetricsDTO::group, RagEvalGroupMetricsDTO::kind)
          .contains(
              tuple("dev", "split"),
              tuple("holdout", "split"),
              tuple("直接事实题", "tag"),
              tuple("知识库外题", "tag"));

      RagEvalGroupMetricsDTO rejectGroup =
          detail.groups().stream()
              .filter(group -> "知识库外题".equals(group.group()))
              .findFirst()
              .orElseThrow();
      assertThat(rejectGroup.metrics().samples()).isEqualTo(8);
      assertThat(rejectGroup.metrics().inScope()).isNull();
      assertThat(rejectGroup.metrics().retrievalMsP50()).isEqualTo(662L);
    }

    @Test
    @DisplayName("空的分组块被跳过，不会产生指标全空的组")
    void skipsEmptyGroupBlocks() {
      Map<String, Object> root = minimalRoot();
      root.put("metrics", Map.of("overall", Map.of("samples", 40), "tag:空组", Map.of()));

      RagEvalBaselineDetailDTO detail = RagEvalBaselineParser.toDetail(root, false);

      assertThat(detail.groups()).extracting(RagEvalGroupMetricsDTO::group).doesNotContain("空组");
      assertThat(detail.markdownAvailable()).isFalse();
    }

    @Test
    @DisplayName("坏例保留召回片段与期望证据，便于人工判断召回问题")
    void mapsBadCases() {
      RagEvalBaselineDetailDTO detail = RagEvalBaselineParser.toDetail(archive(), true);

      assertThat(detail.badCases()).hasSize(1);
      var badCase = detail.badCases().get(0);
      assertThat(badCase.id()).isEqualTo("fact-03");
      assertThat(badCase.reason()).isEqualTo("NO_EVIDENCE_HIT");
      assertThat(badCase.topDocs()).hasSize(1);
      assertThat(badCase.topDocs().get(0).rank()).isEqualTo(1);
      // 旧版归档的 topDocs 没有 score 字段，应为 null 而不是 0
      assertThat(badCase.topDocs().get(0).score()).isNull();
      assertThat(badCase.expectedEvidence()).containsExactly("只满足链表长度而容量不足时优先扩容而不是树化");
    }

    @Test
    @DisplayName("期望证据兼容字符串数组与 {id, text} 对象两种写法")
    void acceptsBothEvidenceShapes() {
      Map<String, Object> root = minimalRoot();
      root.put(
          "badCases",
          List.of(
              Map.of(
                  "id",
                  "fact-03",
                  "expectedEvidence",
                  List.of(Map.of("id", "e1", "text", "对象写法"), "字符串写法"))));

      RagEvalBaselineDetailDTO detail = RagEvalBaselineParser.toDetail(root, true);

      assertThat(detail.badCases().get(0).expectedEvidence())
          .containsExactly("对象写法", "字符串写法");
    }

    @Test
    @DisplayName("环境快照保留指纹清单，并提取模型与 Provider")
    void mapsEnvironment() {
      RagEvalBaselineDetailDTO detail = RagEvalBaselineParser.toDetail(archive(), true);
      var env = detail.environment();

      assertThat(env.model()).isEqualTo("qwen3.5-flash");
      assertThat(env.embeddingModel()).isEqualTo("text-embedding-v3");
      assertThat(env.temperature()).isEqualTo(0.7);
      assertThat(env.defaultChatProvider()).isEqualTo("dashscope");
      assertThat(env.resourceFingerprints())
          .contains("fixtureSha256:corpus-java-backend.md=081a5d118866a258")
          .contains("chunkCount:corpus-java-backend.md=13");
      assertThat(env.tokenUsageNote()).contains("无法取得 usage");
    }

    @Test
    @DisplayName("逐样本明细只保留核对检索质量所需的字段")
    void mapsSamples() {
      RagEvalBaselineDetailDTO detail = RagEvalBaselineParser.toDetail(archive(), true);

      assertThat(detail.samples()).hasSize(1);
      var sample = detail.samples().get(0);
      assertThat(sample.id()).isEqualTo("fact-01");
      assertThat(sample.split()).isEqualTo("dev");
      assertThat(sample.hit()).isTrue();
      assertThat(sample.firstHitRank()).isEqualTo(1);
      assertThat(sample.totalMs()).isEqualTo(26960L);
      assertThat(sample.predictedReject()).isFalse();
    }
  }

  private static String labelOf(String runId) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("runId", runId);
    root.put("environment", Map.of());
    root.put("metrics", Map.of());
    return RagEvalBaselineParser.toSummary(root).label();
  }

  /** 只保留必要键的最小报告，用于构造边界场景而不受完整样本干扰。 */
  private static Map<String, Object> minimalRoot() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("runId", "rag-eval-chunk-800-1788427955758");
    root.put("environment", Map.of());
    return root;
  }

  private static Map<String, Object> archive() {
    Map<String, Object> env = new LinkedHashMap<>();
    env.put("chunkSize", "800(默认)");
    env.put("rewriteEnabled", "false(rag-eval Profile 默认)");
    env.put("mergeOriginalQuery", "false(默认)");
    env.put("springAiVersion", "2.0.0");
    env.put("datasetSha256", "ad3f88c2cb3f5015");
    env.put("gitSha", null);
    env.put("provider:dashscope.model", "qwen3.5-flash");
    env.put("provider:dashscope.embeddingModel", "text-embedding-v3");
    env.put("provider:dashscope.temperature", "0.7");
    env.put("defaultChatProvider", "dashscope");
    env.put("fixtureSha256:corpus-java-backend.md", "081a5d118866a258");
    env.put("chunkCount:corpus-java-backend.md", "13");
    env.put("tokenUsage", "null（当前链路 .content() 无法取得 usage，补齐属 P1-04）");

    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("overall", metrics(40, 32, 87.5, 665L));
    metrics.put("dev", metrics(30, 23, 86.9565, 671L));
    metrics.put("holdout", metrics(10, 9, 88.8889, 664L));
    metrics.put("tag:直接事实题", metrics(12, 12, 83.3333, 449L));
    Map<String, Object> noInScope = new LinkedHashMap<>();
    noInScope.put("samples", 8);
    noInScope.put("harnessErrorCount", 0);
    noInScope.put("retrievalMsP50", 662);
    metrics.put("tag:知识库外题", noInScope);

    Map<String, Object> badCase = new LinkedHashMap<>();
    badCase.put("id", "fact-03");
    badCase.put("question", "HashMap 链表什么时候会转换成红黑树？");
    badCase.put("tags", List.of("直接事实题"));
    badCase.put("reason", "NO_EVIDENCE_HIT");
    badCase.put("detail", "Top-K 内未命中任何证据锚点");
    badCase.put("answerExcerpt", "## HashMap 链表转红黑树的转换机制");
    badCase.put("topDocs", List.of(Map.of("rank", 1, "contentHash", "cf96f37cfbd5a527", "excerpt", "…")));
    badCase.put("expectedEvidence", List.of("只满足链表长度而容量不足时优先扩容而不是树化"));

    Map<String, Object> sample = new LinkedHashMap<>();
    sample.put("id", "fact-01");
    sample.put("split", "dev");
    sample.put("tags", List.of("直接事实题"));
    sample.put("outcome", "ANSWERED");
    sample.put("hit", true);
    sample.put("firstHitRank", 1);
    sample.put("evidenceRecall", 1.0);
    sample.put("retrievalMs", 665);
    sample.put("generationMs", 26235);
    sample.put("totalMs", 26960);
    sample.put("shouldReject", false);
    sample.put("evaluateGeneration", true);
    sample.put("predictedReject", false);

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("runId", "rag-eval-chunk-800-1788427955758");
    root.put("timestamp", "2026-09-03T09:40:08.131821Z");
    root.put("environment", env);
    root.put("metrics", metrics);
    root.put("rejection", Map.of("tp", 0, "fn", 8, "fp", 0, "tn", 10, "samples", 18));
    root.put("badCases", new ArrayList<>(List.of(badCase)));
    root.put("samples", new ArrayList<>(List.of(sample)));
    root.put("datasetVersion", "rag-eval/dataset-v1.jsonl");
    root.put("datasetSize", 40);
    return root;
  }

  private static Map<String, Object> metrics(
      int samples, int inScope, double hitAtK, long retrievalMsP50) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("samples", samples);
    m.put("inScope", inScope);
    m.put("harnessErrorCount", 0);
    m.put("Hit@K(%)", hitAtK);
    m.put("MRR", 0.6964);
    m.put("EvidenceRecall@K", 0.8281);
    m.put("rewriteMsP50", 0);
    m.put("rewriteMsP95", 0);
    m.put("retrievalMsP50", retrievalMsP50);
    m.put("retrievalMsP95", 1014);
    m.put("generationMsP50", 26235);
    m.put("generationMsP95", 30250);
    m.put("endToEndMsP50", 26960);
    m.put("endToEndMsP95", 30887);
    return m;
  }
}
