package ai.interviewer.modules.tokenusage.dto;

/**
 * Token 用量的分组聚合结果，直接作为 JPQL 构造器投影的载体，不对外暴露。
 *
 * @param providerId 模型提供方 ID
 * @param model 模型名
 * @param kind 调用类别（CHAT / EMBEDDING）
 * @param source 调用来源（default / plain / voice / embedding）
 * @param promptTokens 输入 Token 合计
 * @param completionTokens 输出 Token 合计
 * @param totalTokens 总 Token 合计
 * @param calls 调用次数
 * @param avgLatencyMs 平均时延（毫秒）
 * @param failures 失败次数
 */
public record TokenUsageAggregate(
    String providerId,
    String model,
    String kind,
    String source,
    Long promptTokens,
    Long completionTokens,
    Long totalTokens,
    Long calls,
    Double avgLatencyMs,
    Long failures) {}
