package ai.interviewer.modules.tokenusage.dto;

import java.math.BigDecimal;

/**
 * 单个分组维度的用量明细。
 *
 * @param key 分组显示名（如模型名、来源名）
 * @param providerId 提供方 ID
 * @param model 模型名
 * @param kind 调用类别
 * @param source 调用来源
 * @param promptTokens 输入 Token
 * @param completionTokens 输出 Token
 * @param totalTokens 总 Token
 * @param calls 调用次数
 * @param failures 失败次数
 * @param avgLatencyMs 平均时延（按调用次数加权）
 * @param cost 金额；未配置单价时为 {@code null}（表示「未知」而非「免费」）
 * @param priced 是否已配置单价
 */
public record TokenUsageRowDTO(
    String key,
    String providerId,
    String model,
    String kind,
    String source,
    long promptTokens,
    long completionTokens,
    long totalTokens,
    long calls,
    long failures,
    long avgLatencyMs,
    BigDecimal cost,
    boolean priced) {}
