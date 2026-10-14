import assert from 'node:assert/strict';
import test from 'node:test';

import {
  calculateInterviewStats,
  getKnowledgeBaseInterviewCategoryLabel,
  isEvaluateCompleted,
  isEvaluating,
} from './interviewHistoryStats.ts';

const allItems = [
  { status: 'IN_PROGRESS', evaluateStatus: null, overallScore: null },
  { status: 'COMPLETED', evaluateStatus: 'PROCESSING', overallScore: null },
  { status: 'EVALUATED', evaluateStatus: 'COMPLETED', overallScore: 80 },
];

test('普通面试页保持按全部记录统计，不受当前筛选结果影响', () => {
  const stats = calculateInterviewStats(allItems, [allItems[2]], false);

  assert.deepEqual(stats, {
    totalCount: 3,
    completedCount: 1,
    averageScore: 80,
  });
});

test('知识库面试页按筛选结果统计，评估中的记录不计为已完成', () => {
  const filteredItems = [allItems[1], allItems[2]];

  const stats = calculateInterviewStats(allItems, filteredItems, true);

  assert.deepEqual(stats, {
    totalCount: 2,
    completedCount: 1,
    averageScore: 80,
  });
});

test('没有限定方向的知识库面试显示为全部方向', () => {
  assert.equal(getKnowledgeBaseInterviewCategoryLabel(null), '全部方向');
  assert.equal(getKnowledgeBaseInterviewCategoryLabel(''), '全部方向');
  assert.equal(getKnowledgeBaseInterviewCategoryLabel('操作系统'), '操作系统');
});

test('已完成与评估中互斥：status 已 EVALUATED 而 evaluate_status 仍是 PROCESSING 时只算完成', () => {
  // 真实回归场景：异步评估与同步生成报告并发，evaluate_status 被回写成 PROCESSING。
  // 修复前卡片会同时渲染「评估中」和得分，两个判定必须互斥。
  const dirty = { status: 'EVALUATED', evaluateStatus: 'PROCESSING', overallScore: 86 };
  assert.equal(isEvaluateCompleted(dirty), true);
  assert.equal(isEvaluating(dirty), false);

  const evaluating = { status: 'COMPLETED', evaluateStatus: 'PROCESSING', overallScore: null };
  assert.equal(isEvaluateCompleted(evaluating), false);
  assert.equal(isEvaluating(evaluating), true);

  const pending = { status: 'IN_PROGRESS', evaluateStatus: 'PENDING', overallScore: null };
  assert.equal(isEvaluating(pending), true);
});
