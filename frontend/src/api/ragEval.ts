import request from './request';
import type { RagEvalBaseline, RagEvalBaselineDetail } from '../types/ragEval';

export const ragEvalApi = {
  /** 已归档的评测基线列表，按评测时间升序。 */
  baselines: () => request.get<RagEvalBaseline[]>('/api/rag-eval/baselines'),

  /** 单次运行的完整报告。 */
  detail: (runId: string) =>
    request.get<RagEvalBaselineDetail>(`/api/rag-eval/baselines/${encodeURIComponent(runId)}`),

  /** 归档的 Markdown 原报告。 */
  markdown: (runId: string) =>
    request.get<string>(`/api/rag-eval/baselines/${encodeURIComponent(runId)}/markdown`),
};
