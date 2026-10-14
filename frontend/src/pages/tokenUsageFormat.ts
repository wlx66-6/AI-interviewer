import type { TokenUsageRow } from '../types/tokenUsage';

/** 未配置单价时的展示文案：金额是「未知」而不是 0。 */
export const COST_UNKNOWN_LABEL = '未配置单价';

/** Token 数按 k / M 简写，便于卡片阅读。 */
export function formatTokenCount(value: number): string {
  if (!Number.isFinite(value)) {
    return '0';
  }
  const abs = Math.abs(value);
  if (abs >= 1_000_000) {
    return `${roundTo1(value / 1_000_000)}M`;
  }
  if (abs >= 1_000) {
    return `${roundTo1(value / 1_000)}k`;
  }
  return String(Math.round(value));
}

/** 时延按 ms / s 自适应展示。 */
export function formatLatency(millis: number): string {
  if (!Number.isFinite(millis) || millis <= 0) {
    return '-';
  }
  if (millis < 1000) {
    return `${Math.round(millis)} ms`;
  }
  return `${roundTo1(millis / 1000)} s`;
}

/** 金额格式化；未配置单价时返回「未配置单价」而不是 ¥0.0000。 */
export function formatCost(cost: number | null, currency: string, priced: boolean): string {
  if (!priced || cost === null || !Number.isFinite(cost)) {
    return COST_UNKNOWN_LABEL;
  }
  return `${currencySymbol(currency)}${cost.toFixed(4)}`;
}

/** 失败率，无调用时返回 0。 */
export function failureRate(row: Pick<TokenUsageRow, 'calls' | 'failures'>): number {
  if (row.calls <= 0) {
    return 0;
  }
  return row.failures / row.calls;
}

export function formatPercent(ratio: number): string {
  if (!Number.isFinite(ratio) || ratio <= 0) {
    return '0%';
  }
  return `${(ratio * 100).toFixed(1)}%`;
}

/** 组装柱状图数据：取总 Token 最高的前 N 个分组。 */
export function buildTokenChartData(
  rows: TokenUsageRow[],
  limit = 8,
): { name: string; 输入: number; 输出: number }[] {
  return [...rows]
    .sort((a, b) => b.totalTokens - a.totalTokens)
    .slice(0, limit)
    .map((row) => ({
      name: row.key,
      输入: row.promptTokens,
      输出: row.completionTokens,
    }));
}

function currencySymbol(currency: string): string {
  if (currency === 'CNY') {
    return '¥';
  }
  if (currency === 'USD') {
    return '$';
  }
  return `${currency} `;
}

function roundTo1(value: number): string {
  return String(Math.round(value * 10) / 10);
}
