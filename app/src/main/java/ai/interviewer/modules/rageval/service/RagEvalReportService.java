package ai.interviewer.modules.rageval.service;

import ai.interviewer.common.exception.BusinessException;
import ai.interviewer.common.exception.ErrorCode;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDTO;
import ai.interviewer.modules.rageval.dto.RagEvalBaselineDetailDTO;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 读取归档的 RAG 评测基线并对外提供查询。
 *
 * <p>基线是构建时打包进 classpath 的只读快照（{@code rag-eval/baselines/<runId>.json|.md}），
 * 因此这里没有数据库访问、也没有事务，只在首次访问时解析一次并缓存。
 *
 * <p>刻意不做「缺文件就跳过」的容错：能出现在这里的归档都是人工复核过的正式基线，
 * 解析失败说明打包或文件本身出错，应当直接暴露而不是让页面静默少一个运行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalReportService {

  private static final String BASELINE_DIR = "rag-eval/baselines/";
  private static final String BASELINE_PATTERN = "classpath:" + BASELINE_DIR + "*.json";
  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

  /** runId 直接拼进资源路径，先做白名单校验阻断路径穿越。 */
  private static final Pattern SAFE_RUN_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;

  private volatile List<RagEvalBaselineDTO> summaries;
  private final ConcurrentHashMap<String, RagEvalBaselineDetailDTO> details = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> markdowns = new ConcurrentHashMap<>();

  /** 全部归档基线的概要，按评测时间升序。 */
  public List<RagEvalBaselineDTO> list() {
    List<RagEvalBaselineDTO> cached = summaries;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (summaries == null) {
        summaries = loadSummaries();
      }
      return summaries;
    }
  }

  /** 单次运行的完整报告。 */
  public RagEvalBaselineDetailDTO detail(String runId) {
    String safeRunId = requireSafeRunId(runId);
    RagEvalBaselineDetailDTO cached = details.get(safeRunId);
    if (cached != null) {
      return cached;
    }
    // 不在 computeIfAbsent 里做 IO：避免解析期间占住 ConcurrentHashMap 的桶锁。
    RagEvalBaselineDetailDTO loaded = readDetail(safeRunId);
    details.putIfAbsent(safeRunId, loaded);
    return loaded;
  }

  /** 归档的 Markdown 原报告，供页面「查看原文」。 */
  public String markdown(String runId) {
    String safeRunId = requireSafeRunId(runId);
    String cached = markdowns.get(safeRunId);
    if (cached != null) {
      return cached;
    }
    String loaded = readMarkdown(safeRunId);
    markdowns.putIfAbsent(safeRunId, loaded);
    return loaded;
  }

  private List<RagEvalBaselineDTO> loadSummaries() {
    List<RagEvalBaselineDTO> result = new ArrayList<>();
    for (Resource resource : resolveBaselines()) {
      result.add(RagEvalBaselineParser.toSummary(readJson(resource)));
    }
    result.sort(
        Comparator.comparing(
            RagEvalBaselineDTO::timestamp, Comparator.nullsFirst(Comparator.naturalOrder())));
    log.info("[RagEval] 已加载 {} 组归档评测基线", result.size());
    return List.copyOf(result);
  }

  private RagEvalBaselineDetailDTO readDetail(String runId) {
    Map<String, Object> root = readJson(resolve(runId + ".json"));
    return RagEvalBaselineParser.toDetail(root, resolve(runId + ".md").exists());
  }

  private String readMarkdown(String runId) {
    Resource resource = resolve(runId + ".md");
    if (!resource.exists()) {
      throw new BusinessException(
          ErrorCode.RAG_EVAL_BASELINE_NOT_FOUND, "该基线没有归档 Markdown 报告: " + runId);
    }
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new BusinessException(
          ErrorCode.RAG_EVAL_BASELINE_READ_FAILED, "读取 RAG 评测报告失败: " + runId, e);
    }
  }

  private Map<String, Object> readJson(Resource resource) {
    if (!resource.exists()) {
      throw new BusinessException(
          ErrorCode.RAG_EVAL_BASELINE_NOT_FOUND, "RAG 评测基线不存在: " + resource.getFilename());
    }
    try (InputStream in = resource.getInputStream()) {
      Map<String, Object> root = objectMapper.readValue(in, JSON_OBJECT);
      if (root == null || root.isEmpty()) {
        throw new BusinessException(
            ErrorCode.RAG_EVAL_BASELINE_READ_FAILED,
            "RAG 评测基线内容为空: " + resource.getFilename());
      }
      return root;
    } catch (IOException e) {
      throw new BusinessException(
          ErrorCode.RAG_EVAL_BASELINE_READ_FAILED,
          "读取 RAG 评测基线失败: " + resource.getFilename(),
          e);
    }
  }

  private Resource[] resolveBaselines() {
    try {
      return patternResolver().getResources(BASELINE_PATTERN);
    } catch (IOException e) {
      throw new BusinessException(
          ErrorCode.RAG_EVAL_BASELINE_READ_FAILED, "扫描 RAG 评测基线目录失败", e);
    }
  }

  private Resource resolve(String fileName) {
    return patternResolver().getResource(CLASSPATH_PREFIX + BASELINE_DIR + fileName);
  }

  private ResourcePatternResolver patternResolver() {
    return resourceLoader instanceof ResourcePatternResolver resolver
        ? resolver
        : new PathMatchingResourcePatternResolver(resourceLoader);
  }

  private static String requireSafeRunId(String runId) {
    if (runId == null || runId.contains("..") || !SAFE_RUN_ID.matcher(runId).matches()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的 runId: " + runId);
    }
    return runId;
  }
}
