package ai.interviewer.modules.rageval.dto;

import java.util.List;

/**
 * 单次评测运行的完整报告。
 *
 * @param summary 与列表接口一致的头信息与总体指标
 * @param groups 按切分与题型分组的指标，不含 overall（已在 summary 中）
 * @param markdownAvailable 是否归档了 Markdown 原报告，可供页面「查看原文」
 */
public record RagEvalBaselineDetailDTO(
    RagEvalBaselineDTO summary,
    List<RagEvalGroupMetricsDTO> groups,
    List<RagEvalBadCaseDTO> badCases,
    List<RagEvalSampleDTO> samples,
    RagEvalEnvironmentDTO environment,
    boolean markdownAvailable) {}
