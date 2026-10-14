import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MISSING,
  badCaseReasonLabel,
  buildChunkComparison,
  describeRunConfig,
  formatMillis,
  formatPercent,
  formatRatio,
  formatRatioAsPercent,
  groupLabel,
} from './ragEvalFormat.ts';
import type { RagEvalBaseline } from '../types/ragEval';

function run(
  label: string,
  chunkSize: number | null,
  hitAtK: number | null,
  mrr: number | null,
  evidenceRecallAtK: number | null,
): RagEvalBaseline {
  return {
    runId: `rag-eval-${label}`,
    label,
    timestamp: null,
    chunkSize,
    rewriteEnabled: false,
    mergeOriginalQuery: false,
    datasetVersion: null,
    datasetSize: 40,
    evaluatorVersion: null,
    overall: {
      samples: 40,
      inScope: 32,
      harnessErrorCount: 0,
      hitAtK,
      mrr,
      evidenceRecallAtK,
      rewriteMsP50: 0,
      rewriteMsP95: 0,
      retrievalMsP50: 0,
      retrievalMsP95: 0,
      generationMsP50: 0,
      generationMsP95: 0,
      endToEndMsP50: 0,
      endToEndMsP95: 0,
    },
    dev: null,
    holdout: null,
    rejection: null,
    badCaseCount: 0,
  };
}

test('缺失的指标显示为占位符，而不是 0', () => {
  assert.equal(formatPercent(null), MISSING);
  assert.equal(formatRatio(null), MISSING);
  assert.equal(formatMillis(null), MISSING);
  assert.equal(formatRatioAsPercent(null), MISSING);
  assert.equal(badCaseReasonLabel(null), MISSING);
});

test('百分数与比值按各自精度格式化', () => {
  assert.equal(formatPercent(87.5), '87.5%');
  assert.equal(formatPercent(100), '100.0%');
  assert.equal(formatRatio(0.6964), '0.696');
  assert.equal(formatRatioAsPercent(0.8281), '82.8%');
});

test('耗时按毫秒与秒自适应显示', () => {
  assert.equal(formatMillis(665), '665 ms');
  assert.equal(formatMillis(999), '999 ms');
  assert.equal(formatMillis(30250), '30.3 s');
});

test('分块对照只保留记录了分块的运行，并按分块升序换算成百分数', () => {
  const runs = [
    run('Chunk 800', 800, 87.5, 0.6964, 0.8281),
    run('改写 + 双路融合', null, 96.875, 0.6961, 0.9531),
    run('Chunk 400', 400, 90.625, 0.7333, 0.8906),
    run('Chunk 1200', 1200, 93.75, 0.5951, 0.9063),
  ];

  assert.deepEqual(buildChunkComparison(runs), [
    { name: 'Chunk 400', 'Hit@K': 90.625, 证据召回率: 89.1, MRR: 73.3 },
    { name: 'Chunk 800', 'Hit@K': 87.5, 证据召回率: 82.8, MRR: 69.6 },
    { name: 'Chunk 1200', 'Hit@K': 93.75, 证据召回率: 90.6, MRR: 59.5 },
  ]);
});

test('没有分块记录的运行不进入对照图', () => {
  assert.deepEqual(buildChunkComparison([run('基线配置', null, 96.875, 0.68, 0.95)]), []);
});

test('配置摘要按分块与开关拼装，未记录的开关不臆测', () => {
  assert.equal(
    describeRunConfig({ chunkSize: 800, rewriteEnabled: false, mergeOriginalQuery: false }),
    'Chunk 800 · 不改写',
  );
  assert.equal(
    describeRunConfig({ chunkSize: 800, rewriteEnabled: true, mergeOriginalQuery: true }),
    'Chunk 800 · 改写 + 融合',
  );
  assert.equal(
    describeRunConfig({ chunkSize: 800, rewriteEnabled: true, mergeOriginalQuery: false }),
    'Chunk 800 · 改写',
  );
  assert.equal(
    describeRunConfig({ chunkSize: null, rewriteEnabled: null, mergeOriginalQuery: null }),
    '分块未记录',
  );
});

test('切分组给出中文说明，题型标签保持原样', () => {
  assert.equal(groupLabel('split', 'dev'), '调参集（dev）');
  assert.equal(groupLabel('split', 'holdout'), '留出集（holdout）');
  assert.equal(groupLabel('tag', '直接事实题'), '直接事实题');
});

test('坏例原因翻译为中文，未知原因原样透出', () => {
  assert.equal(badCaseReasonLabel('NO_EVIDENCE_HIT'), '未召回任何证据');
  assert.equal(badCaseReasonLabel('OOS_NOT_REJECTED'), '库外问题未拒答');
  assert.equal(badCaseReasonLabel('SOMETHING_NEW'), 'SOMETHING_NEW');
});
