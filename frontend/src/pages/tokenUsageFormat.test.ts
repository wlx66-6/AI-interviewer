import assert from 'node:assert/strict';
import test from 'node:test';

import {
  COST_UNKNOWN_LABEL,
  buildTokenChartData,
  failureRate,
  formatCost,
  formatLatency,
  formatPercent,
  formatTokenCount,
} from './tokenUsageFormat.ts';
import type { TokenUsageRow } from '../types/tokenUsage';

function row(overrides: Partial<TokenUsageRow> = {}): TokenUsageRow {
  return {
    key: 'qwen-plus',
    providerId: 'dashscope',
    model: 'qwen-plus',
    kind: 'CHAT',
    source: 'default',
    promptTokens: 0,
    completionTokens: 0,
    totalTokens: 0,
    calls: 0,
    failures: 0,
    avgLatencyMs: 0,
    cost: null,
    priced: false,
    ...overrides,
  };
}

test('Token 数按 k / M 简写', () => {
  assert.equal(formatTokenCount(0), '0');
  assert.equal(formatTokenCount(999), '999');
  assert.equal(formatTokenCount(1500), '1.5k');
  assert.equal(formatTokenCount(2_400_000), '2.4M');
  assert.equal(formatTokenCount(Number.NaN), '0');
});

test('时延按 ms / s 自适应', () => {
  assert.equal(formatLatency(0), '-');
  assert.equal(formatLatency(850), '850 ms');
  assert.equal(formatLatency(2500), '2.5 s');
});

test('未配置单价时展示「未配置单价」而不是 ¥0.0000', () => {
  assert.equal(formatCost(null, 'CNY', false), COST_UNKNOWN_LABEL);
  assert.equal(formatCost(0.0123, 'CNY', true), '¥0.0123');
  assert.equal(formatCost(1.5, 'USD', true), '$1.5000');
});

test('失败率在无调用时返回 0', () => {
  assert.equal(failureRate(row()), 0);
  assert.equal(failureRate(row({ calls: 4, failures: 1 })), 0.25);
  assert.equal(formatPercent(0.25), '25.0%');
  assert.equal(formatPercent(0), '0%');
});

test('柱状图只取总 Token 最高的前 N 个分组', () => {
  const rows = [
    row({ key: 'small', totalTokens: 10, promptTokens: 6, completionTokens: 4 }),
    row({ key: 'big', totalTokens: 900, promptTokens: 500, completionTokens: 400 }),
    row({ key: 'mid', totalTokens: 100, promptTokens: 60, completionTokens: 40 }),
  ];

  const data = buildTokenChartData(rows, 2);

  assert.deepEqual(
    data.map((item) => item.name),
    ['big', 'mid'],
  );
  assert.deepEqual(data[0], { name: 'big', 输入: 500, 输出: 400 });
});
