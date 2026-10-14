/** Token 用量统计的分组维度。 */
export type TokenUsageGroupBy = 'model' | 'provider' | 'kind' | 'source';

/** 单个分组的用量明细。 */
export interface TokenUsageRow {
  key: string;
  providerId: string | null;
  model: string | null;
  kind: string;
  source: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  calls: number;
  failures: number;
  avgLatencyMs: number;
  /** 未配置单价时为 null，表示「未知」而不是「免费」。 */
  cost: number | null;
  priced: boolean;
}

/** 用量统计响应。 */
export interface TokenUsageStats {
  from: string;
  to: string;
  groupBy: TokenUsageGroupBy;
  currency: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  calls: number;
  failures: number;
  totalCost: number | null;
  /** 为 false 时说明存在未配置单价的模型，金额不完整。 */
  costComplete: boolean;
  rows: TokenUsageRow[];
}

/** 单个模型的单价（每千 Token）。 */
export interface ModelPrice {
  model: string;
  inputPer1k: number | null;
  outputPer1k: number | null;
}

/** 已配置的单价表。 */
export interface ModelPricing {
  currency: string;
  models: ModelPrice[];
}
