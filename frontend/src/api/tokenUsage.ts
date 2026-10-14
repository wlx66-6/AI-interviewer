import request from './request';
import type { ModelPricing, TokenUsageGroupBy, TokenUsageStats } from '../types/tokenUsage';

export interface TokenUsageStatsParams {
  /** 起始日期（含），格式 yyyy-MM-dd */
  from?: string;
  /** 结束日期（含），格式 yyyy-MM-dd */
  to?: string;
  groupBy?: TokenUsageGroupBy;
}

export const tokenUsageApi = {
  stats: (params: TokenUsageStatsParams = {}) =>
    request.get<TokenUsageStats>('/api/token-usage/stats', { params }),

  pricing: () => request.get<ModelPricing>('/api/token-usage/pricing'),
};
