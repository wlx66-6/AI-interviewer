package ai.interviewer.modules.rageval.dto;

/**
 * 按切分或题型分组的一组指标。
 *
 * @param group 分组名：{@code dev} / {@code holdout} / 题型标签（如 {@code 直接事实题}）
 */
public record RagEvalGroupMetricsDTO(String group, String kind, RagEvalMetricsDTO metrics) {}
