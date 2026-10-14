package ai.interviewer.modules.rageval.dto;

/**
 * 一条被召回的片段（或一条期望证据）。
 *
 * @param rank 召回排名，从 1 开始；期望证据无排名时为 null
 * @param contentHash 片段内容哈希，用于在报告中稳定标识片段而不暴露原文
 * @param score 相似度分数，部分归档报告未记录时为 null
 * @param excerpt 片段摘要
 */
public record RagEvalDocDTO(Integer rank, String contentHash, Double score, String excerpt) {}
