import type { RagEvalBaseline } from '../types/ragEval';

/** 指标缺失时的占位符：归档报告里缺字段表示「未统计」，不是 0。 */
export const MISSING = '—';

/** 坏例原因的中文说明。 */
const BAD_CASE_REASONS: Record<string, string> = {
  NO_EVIDENCE_HIT: '未召回任何证据',
  LOW_FIRST_RANK: '首个命中排名靠后',
  EVIDENCE_INCOMPLETE: '证据不完整',
  OOS_NOT_REJECTED: '库外问题未拒答',
};

/** 百分数指标（Hit@K 本身已是百分数）。 */
export function formatPercent(value: number | null, digits = 1): string {
  if (value === null || !Number.isFinite(value)) {
    return MISSING;
  }
  return `${value.toFixed(digits)}%`;
}

/** 0~1 的比值指标（MRR / EvidenceRecall / 拒答 P-R-F1）。 */
export function formatRatio(value: number | null, digits = 3): string {
  if (value === null || !Number.isFinite(value)) {
    return MISSING;
  }
  return value.toFixed(digits);
}

/** 耗时按场景缩放：毫秒级看整数，秒级看一位小数。 */
export function formatMillis(value: number | null): string {
  if (value === null || !Number.isFinite(value)) {
    return MISSING;
  }
  if (value < 1000) {
    return `${Math.round(value)} ms`;
  }
  return `${roundTo1(value / 1000)} s`;
}

export function formatRatioAsPercent(value: number | null): string {
  if (value === null || !Number.isFinite(value)) {
    return MISSING;
  }
  return `${roundTo1(value * 100)}%`;
}

/**
 * 分块大小对照数据：只取记录了 chunkSize 的运行，按分块从小到大排列。
 * 三个指标统一换算成 0~100，才能放进同一张图里比较高低。
 */
export function buildChunkComparison(
  runs: RagEvalBaseline[],
): { name: string; 'Hit@K': number; 证据召回率: number; MRR: number }[] {
  return runs
    .filter((run) => run.chunkSize !== null && run.overall !== null)
    .slice()
    .sort((a, b) => (a.chunkSize ?? 0) - (b.chunkSize ?? 0))
    .map((run) => ({
      name: run.label,
      'Hit@K': run.overall?.hitAtK ?? 0,
      证据召回率: roundTo1((run.overall?.evidenceRecallAtK ?? 0) * 100),
      MRR: roundTo1((run.overall?.mrr ?? 0) * 100),
    }));
}

/** 一行配置摘要，例如「Chunk 800 · 不改写」。 */
export function describeRunConfig(
  run: Pick<RagEvalBaseline, 'chunkSize' | 'rewriteEnabled' | 'mergeOriginalQuery'>,
): string {
  const parts = [run.chunkSize === null ? '分块未记录' : `Chunk ${run.chunkSize}`];
  if (run.rewriteEnabled === true) {
    parts.push(run.mergeOriginalQuery === true ? '改写 + 融合' : '改写');
  } else if (run.rewriteEnabled === false) {
    parts.push('不改写');
  }
  return parts.join(' · ');
}

/** 分组标题：切分组用中文说明，题型标签直接用数据集里的标签名。 */
export function groupLabel(kind: string, group: string): string {
  if (kind !== 'split') {
    return group;
  }
  return group === 'holdout' ? '留出集（holdout）' : '调参集（dev）';
}

export function badCaseReasonLabel(reason: string | null): string {
  if (reason === null) {
    return MISSING;
  }
  return BAD_CASE_REASONS[reason] ?? reason;
}

function roundTo1(value: number): number {
  return Math.round(value * 10) / 10;
}
