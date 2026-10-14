package ai.interviewer.modules.rageval.dto;

/**
 * 一组已归档的 RAG 评测基线（列表项）。
 *
 * @param label 便于阅读的配置名，由 runId 与配置推导，例如 {@code Chunk 400}
 * @param evaluatorVersion 指标口径版本；早期报告没有该字段时为 null
 * @param badCaseCount 坏例条数
 */
public record RagEvalBaselineDTO(
    String runId,
    String label,
    String timestamp,
    Integer chunkSize,
    Boolean rewriteEnabled,
    Boolean mergeOriginalQuery,
    String datasetVersion,
    Integer datasetSize,
    String evaluatorVersion,
    RagEvalMetricsDTO overall,
    RagEvalMetricsDTO dev,
    RagEvalMetricsDTO holdout,
    RagEvalRejectionDTO rejection,
    Integer badCaseCount) {}
