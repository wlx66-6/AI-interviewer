package ai.interviewer.modules.rageval.service;

import ai.interviewer.modules.rageval.dto.RagEvalBadCaseDTO;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDTO;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDetailDTO;
import ai.interviewer.modules.rageval.dto.RagEvalDocDTO;
import ai.interviewer.modules.rageval.dto.RagEvalEnvironmentDTO;
import ai.interviewer.modules.rageval.dto.RagEvalGroupMetricsDTO;
import ai.interviewer.modules.rageval.dto.RagEvalMetricsDTO;
import ai.interviewer.modules.rageval.dto.RagEvalRejectionDTO;
import ai.interviewer.modules.rageval.dto.RagEvalSampleDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把归档的 RAG 评测报告 JSON 转成对外 DTO。
 *
 * <p>纯函数、不碰 IO，因此可以脱离 Spring 与文件系统直接单元测试。设计上按「归档文件可能来自
 * 不同版本的评估器」处理：缺失字段一律映射为 {@code null}，不做默认值填充——「没统计」和
 * 「统计结果是 0」在报告里是两回事。
 */
final class RagEvalBaselineParser {

  private static final String TAG_PREFIX = "tag:";
  private static final String PROVIDER_PREFIX = "provider:";
  private static final String FINGERPRINT_KEYWORD = "Sha256";
  private static final String CHUNK_COUNT_PREFIX = "chunkCount:";
  private static final Pattern CHUNK_IN_RUN_ID = Pattern.compile("chunk-(\\d+)");
  private static final Pattern LEADING_INT = Pattern.compile("^\\s*(\\d+)");

  private RagEvalBaselineParser() {}

  /** 列表项：只保留头信息、总体指标与拒答矩阵。 */
  static RagEvalBaselineDTO toSummary(Map<String, Object> root) {
    Map<String, Object> env = asMap(root.get("environment"));
    Map<String, Object> metrics = asMap(root.get("metrics"));
    String runId = asString(root.get("runId"));
    Integer chunkSize = asInteger(env.get("chunkSize"));
    Boolean rewriteEnabled = asBooleanFlag(env.get("rewriteEnabled"));
    Boolean mergeOriginalQuery = asBooleanFlag(env.get("mergeOriginalQuery"));
    return new RagEvalBaselineDTO(
        runId,
        label(runId, chunkSize),
        asString(root.get("timestamp")),
        chunkSize,
        rewriteEnabled,
        mergeOriginalQuery,
        asString(root.get("datasetVersion")),
        asInteger(root.get("datasetSize")),
        asString(root.get("evaluatorVersion")),
        toMetrics(metrics.get("overall")),
        toMetrics(metrics.get("dev")),
        toMetrics(metrics.get("holdout")),
        toRejection(root.get("rejection")),
        asList(root.get("badCases")).size());
  }

  /** 详情：在列表项之上补分组指标、坏例、逐样本明细与环境快照。 */
  static RagEvalBaselineDetailDTO toDetail(Map<String, Object> root, boolean markdownAvailable) {
    Map<String, Object> metrics = asMap(root.get("metrics"));
    List<RagEvalGroupMetricsDTO> groups = new ArrayList<>();
    addGroup(groups, "dev", "split", metrics.get("dev"));
    addGroup(groups, "holdout", "split", metrics.get("holdout"));
    for (Map.Entry<String, Object> entry : metrics.entrySet()) {
      if (entry.getKey().startsWith(TAG_PREFIX)) {
        addGroup(groups, entry.getKey().substring(TAG_PREFIX.length()), "tag", entry.getValue());
      }
    }
    return new RagEvalBaselineDetailDTO(
        toSummary(root),
        List.copyOf(groups),
        toBadCases(root.get("badCases")),
        toSamples(root.get("samples")),
        toEnvironment(asMap(root.get("environment"))),
        markdownAvailable);
  }

  private static void addGroup(
      List<RagEvalGroupMetricsDTO> groups, String name, String kind, Object value) {
    RagEvalMetricsDTO metrics = toMetrics(value);
    if (metrics != null) {
      groups.add(new RagEvalGroupMetricsDTO(name, kind, metrics));
    }
  }

  private static RagEvalMetricsDTO toMetrics(Object value) {
    Map<String, Object> m = asMap(value);
    if (m.isEmpty()) {
      return null;
    }
    return new RagEvalMetricsDTO(
        asInteger(m.get("samples")),
        asInteger(m.get("inScope")),
        asInteger(m.get("harnessErrorCount")),
        asDouble(m.get("Hit@K(%)")),
        asDouble(m.get("MRR")),
        asDouble(m.get("EvidenceRecall@K")),
        asLong(m.get("rewriteMsP50")),
        asLong(m.get("rewriteMsP95")),
        asLong(m.get("retrievalMsP50")),
        asLong(m.get("retrievalMsP95")),
        asLong(m.get("generationMsP50")),
        asLong(m.get("generationMsP95")),
        asLong(m.get("endToEndMsP50")),
        asLong(m.get("endToEndMsP95")));
  }

  private static RagEvalRejectionDTO toRejection(Object value) {
    Map<String, Object> m = asMap(value);
    if (m.isEmpty()) {
      return null;
    }
    return new RagEvalRejectionDTO(
        asInteger(m.get("tp")),
        asInteger(m.get("fn")),
        asInteger(m.get("fp")),
        asInteger(m.get("tn")),
        asInteger(m.get("samples")),
        asDouble(m.get("accuracy")),
        asDouble(m.get("precision")),
        asDouble(m.get("recall")),
        asDouble(m.get("f1")));
  }

  private static List<RagEvalBadCaseDTO> toBadCases(Object value) {
    List<RagEvalBadCaseDTO> cases = new ArrayList<>();
    for (Map<String, Object> c : asMapList(value)) {
      cases.add(
          new RagEvalBadCaseDTO(
              asString(c.get("id")),
              asString(c.get("question")),
              asStringList(c.get("tags")),
              asString(c.get("reason")),
              asString(c.get("detail")),
              asString(c.get("answerExcerpt")),
              toDocs(c.get("topDocs")),
              asStringList(c.get("expectedEvidence"))));
    }
    return List.copyOf(cases);
  }

  private static List<RagEvalDocDTO> toDocs(Object value) {
    List<RagEvalDocDTO> docs = new ArrayList<>();
    for (Map<String, Object> d : asMapList(value)) {
      docs.add(
          new RagEvalDocDTO(
              asInteger(d.get("rank")),
              asString(d.get("contentHash")),
              asDouble(d.get("score")),
              asString(d.get("excerpt"))));
    }
    return List.copyOf(docs);
  }

  private static List<RagEvalSampleDTO> toSamples(Object value) {
    List<RagEvalSampleDTO> samples = new ArrayList<>();
    for (Map<String, Object> s : asMapList(value)) {
      samples.add(
          new RagEvalSampleDTO(
              asString(s.get("id")),
              asString(s.get("split")),
              asStringList(s.get("tags")),
              asString(s.get("outcome")),
              asBoolean(s.get("hit")),
              asInteger(s.get("firstHitRank")),
              asDouble(s.get("evidenceRecall")),
              asLong(s.get("retrievalMs")),
              asLong(s.get("generationMs")),
              asLong(s.get("totalMs")),
              asBoolean(s.get("shouldReject")),
              asBoolean(s.get("evaluateGeneration")),
              asBoolean(s.get("predictedReject"))));
    }
    return List.copyOf(samples);
  }

  private static RagEvalEnvironmentDTO toEnvironment(Map<String, Object> env) {
    String model = null;
    String embeddingModel = null;
    Double temperature = null;
    Map<String, String> fingerprints = new TreeMap<>();
    for (Map.Entry<String, Object> entry : env.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(PROVIDER_PREFIX)) {
        String[] field = key.substring(PROVIDER_PREFIX.length()).split("\\.", 2);
        if (field.length == 2) {
          if ("model".equals(field[1]) && model == null) {
            model = asString(entry.getValue());
          } else if ("embeddingModel".equals(field[1]) && embeddingModel == null) {
            embeddingModel = asString(entry.getValue());
          } else if ("temperature".equals(field[1]) && temperature == null) {
            temperature = asDouble(entry.getValue());
          }
        }
      } else if (key.contains(FINGERPRINT_KEYWORD) || key.startsWith(CHUNK_COUNT_PREFIX)) {
        fingerprints.put(key, asString(entry.getValue()));
      }
    }
    List<String> resourceFingerprints = new ArrayList<>();
    fingerprints.forEach((key, value) -> resourceFingerprints.add(key + "=" + value));
    return new RagEvalEnvironmentDTO(
        model,
        embeddingModel,
        temperature,
        asString(env.get("defaultChatProvider")),
        asString(env.get("defaultEmbeddingProvider")),
        asInteger(env.get("chunkSize")),
        asBooleanFlag(env.get("rewriteEnabled")),
        asBooleanFlag(env.get("mergeOriginalQuery")),
        asString(env.get("springAiVersion")),
        asString(env.get("datasetSha256")),
        asString(env.get("gitSha")),
        List.copyOf(resourceFingerprints),
        asString(env.get("tokenUsage")));
  }

  /**
   * 由 runId 推导人类可读的配置名：归档文件名本身已经编码了这次运行改了什么
   * （分块大小 / 双路融合 / 仅改写），比从可能缺失的环境快照里猜更可靠。
   */
  private static String label(String runId, Integer chunkSize) {
    if (runId == null) {
      return "未命名运行";
    }
    Matcher chunk = CHUNK_IN_RUN_ID.matcher(runId);
    if (chunk.find()) {
      return "Chunk " + chunk.group(1);
    }
    if (runId.contains("rewrite-only")) {
      return "仅开启改写";
    }
    if (runId.contains("merge")) {
      return "改写 + 双路融合";
    }
    if (runId.contains("baseline")) {
      return chunkSize == null ? "基线配置" : "Chunk " + chunkSize;
    }
    return runId;
  }

  private static Map<String, Object> asMap(Object value) {
    if (value instanceof Map<?, ?> raw) {
      Map<String, Object> result = new LinkedHashMap<>();
      raw.forEach((k, v) -> result.put(String.valueOf(k), v));
      return result;
    }
    return Map.of();
  }

  private static List<Map<String, Object>> asMapList(Object value) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : asList(value)) {
      if (item instanceof Map<?, ?>) {
        result.add(asMap(item));
      }
    }
    return result;
  }

  private static List<Object> asList(Object value) {
    if (value instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    return List.of();
  }

  private static List<String> asStringList(Object value) {
    List<String> result = new ArrayList<>();
    for (Object item : asList(value)) {
      // expectedEvidence 在不同版本的报告里既可能是字符串，也可能是 {id, text} 对象
      String text = item instanceof Map<?, ?> map ? asString(asMap(map).get("text")) : asString(item);
      if (text != null) {
        result.add(text);
      }
    }
    return List.copyOf(result);
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Integer asInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    Matcher matcher = value == null ? null : LEADING_INT.matcher(String.valueOf(value));
    return matcher != null && matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
  }

  private static Long asLong(Object value) {
    return value instanceof Number number ? number.longValue() : null;
  }

  private static Double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      return null;
    }
    try {
      return Double.valueOf(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Boolean asBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return null;
  }

  /** 环境快照里的开关是「值 + 说明」的字符串，例如 {@code "false(rag-eval Profile 默认)"}。 */
  private static Boolean asBooleanFlag(Object value) {
    String text = asString(value);
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    if (trimmed.startsWith("true")) {
      return Boolean.TRUE;
    }
    if (trimmed.startsWith("false")) {
      return Boolean.FALSE;
    }
    return null;
  }
}
