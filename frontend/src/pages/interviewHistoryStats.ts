export interface InterviewStatsItem {
  status: string;
  evaluateStatus?: string | null;
  overallScore: number | null;
}

export interface InterviewStats {
  totalCount: number;
  completedCount: number;
  averageScore: number;
}

export function isCompletedStatus(status: string): boolean {
  return status === 'COMPLETED' || status === 'EVALUATED';
}

export function isEvaluateCompleted(item: InterviewStatsItem): boolean {
  return item.evaluateStatus === 'COMPLETED' || item.status === 'EVALUATED';
}

/**
 * 是否仍在评估中。
 *
 * 必须与 {@link isEvaluateCompleted} 互斥：后端把 status 推进到 EVALUATED 之后，
 * evaluate_status 可能仍停留在 PENDING/PROCESSING（例如异步评估与同步生成报告
 * 并发的历史数据）。若两个判定各自独立，卡片会同时渲染「评估中」和得分，
 * 所以这里显式排除已完成的情况。
 */
export function isEvaluating(item: InterviewStatsItem): boolean {
  if (isEvaluateCompleted(item)) {
    return false;
  }
  return item.evaluateStatus === 'PENDING' || item.evaluateStatus === 'PROCESSING';
}

export function getKnowledgeBaseInterviewCategoryLabel(category?: string | null): string {
  return category?.trim() || '全部方向';
}

export function calculateInterviewStats<T extends InterviewStatsItem>(
  allItems: readonly T[],
  filteredItems: readonly T[],
  isKnowledgeBaseView: boolean,
): InterviewStats {
  const source = isKnowledgeBaseView ? filteredItems : allItems;
  const evaluated = source.filter(isEvaluateCompleted);
  const totalScore = evaluated.reduce((sum, item) => sum + (item.overallScore ?? 0), 0);

  return {
    totalCount: source.length,
    completedCount: evaluated.length,
    averageScore: evaluated.length > 0 ? Math.round(totalScore / evaluated.length) : 0,
  };
}
