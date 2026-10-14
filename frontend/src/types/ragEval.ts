/**
 * RAG 评测报告类型。
 *
 * 指标字段可以为 null：归档报告由不同版本的评估器产出，「没有该字段」表示未统计，
 * 与「统计结果是 0」是两回事，界面上必须区分。
 */

/** 一组样本的检索质量指标。 */
export interface RagEvalMetrics {
  samples: number | null;
  inScope: number | null;
  harnessErrorCount: number | null;
  /** 前 K 个片段中至少命中一个证据锚点的样本占比（百分数）。 */
  hitAtK: number | null;
  /** 平均倒数排名；未命中记 0。 */
  mrr: number | null;
  /** 命中锚点数 / 全部锚点数。 */
  evidenceRecallAtK: number | null;
  rewriteMsP50: number | null;
  rewriteMsP95: number | null;
  retrievalMsP50: number | null;
  retrievalMsP95: number | null;
  generationMsP50: number | null;
  generationMsP95: number | null;
  endToEndMsP50: number | null;
  endToEndMsP95: number | null;
}

/** 知识库外问题的拒答混淆矩阵。 */
export interface RagEvalRejection {
  tp: number | null;
  fn: number | null;
  fp: number | null;
  tn: number | null;
  samples: number | null;
  accuracy: number | null;
  precision: number | null;
  recall: number | null;
  f1: number | null;
}

/** 一组已归档的评测基线（列表项）。 */
export interface RagEvalBaseline {
  runId: string;
  label: string;
  timestamp: string | null;
  chunkSize: number | null;
  rewriteEnabled: boolean | null;
  mergeOriginalQuery: boolean | null;
  datasetVersion: string | null;
  datasetSize: number | null;
  evaluatorVersion: string | null;
  overall: RagEvalMetrics | null;
  dev: RagEvalMetrics | null;
  holdout: RagEvalMetrics | null;
  rejection: RagEvalRejection | null;
  badCaseCount: number | null;
}

/** 一条被召回的片段。 */
export interface RagEvalDoc {
  rank: number | null;
  contentHash: string | null;
  score: number | null;
  excerpt: string | null;
}

/** 坏例：未达标的样本。 */
export interface RagEvalBadCase {
  id: string | null;
  question: string | null;
  tags: string[];
  reason: string | null;
  detail: string | null;
  answerExcerpt: string | null;
  topDocs: RagEvalDoc[];
  expectedEvidence: string[];
}

/** 逐样本检索结果明细。 */
export interface RagEvalSample {
  id: string | null;
  split: string | null;
  tags: string[];
  outcome: string | null;
  hit: boolean | null;
  firstHitRank: number | null;
  evidenceRecall: number | null;
  retrievalMs: number | null;
  generationMs: number | null;
  totalMs: number | null;
  shouldReject: boolean | null;
  evaluateGeneration: boolean | null;
  predictedReject: boolean | null;
}

/** 按切分或题型分组的一组指标。 */
export interface RagEvalGroup {
  group: string;
  kind: 'split' | 'tag';
  metrics: RagEvalMetrics;
}

/** 一次评测运行的环境快照。 */
export interface RagEvalEnvironment {
  model: string | null;
  embeddingModel: string | null;
  temperature: number | null;
  defaultChatProvider: string | null;
  defaultEmbeddingProvider: string | null;
  chunkSize: number | null;
  rewriteEnabled: boolean | null;
  mergeOriginalQuery: boolean | null;
  springAiVersion: string | null;
  datasetSha256: string | null;
  gitSha: string | null;
  /** 语料、Prompt 与分块数的指纹清单，形如 `fixtureSha256:xx.md=hash`。 */
  resourceFingerprints: string[];
  tokenUsageNote: string | null;
}

/** 单次运行的完整报告。 */
export interface RagEvalBaselineDetail {
  summary: RagEvalBaseline;
  groups: RagEvalGroup[];
  badCases: RagEvalBadCase[];
  samples: RagEvalSample[];
  environment: RagEvalEnvironment;
  markdownAvailable: boolean;
}
