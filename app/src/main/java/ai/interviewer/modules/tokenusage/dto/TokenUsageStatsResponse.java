package ai.interviewer.modules.tokenusage.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Token 用量统计响应。
 *
 * @param from 统计窗口起始日（含）
 * @param to 统计窗口结束日（含）
 * @param groupBy 分组维度：model / provider / kind / source
 * @param currency 金额币种
 * @param promptTokens 输入 Token 合计
 * @param completionTokens 输出 Token 合计
 * @param totalTokens 总 Token 合计
 * @param calls 调用次数合计
 * @param failures 失败次数合计
 * @param totalCost 已配置单价部分的金额合计；{@code costComplete=false} 时该值不完整
 * @param costComplete 是否所有分组都配置了单价（为 false 时前端需提示金额不完整）
 * @param rows 分组明细，按总 Token 倒序
 */
public record TokenUsageStatsResponse(
    LocalDate from,
    LocalDate to,
    String groupBy,
    String currency,
    long promptTokens,
    long completionTokens,
    long totalTokens,
    long calls,
    long failures,
    BigDecimal totalCost,
    boolean costComplete,
    List<TokenUsageRowDTO> rows) {}
