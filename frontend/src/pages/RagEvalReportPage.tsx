import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  AlertCircle,
  BookOpen,
  FileText,
  Info,
  Loader2,
  RefreshCw,
  Target,
  TriangleAlert,
} from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { ragEvalApi } from '../api/ragEval';
import type {
  RagEvalBadCase,
  RagEvalBaseline,
  RagEvalBaselineDetail,
  RagEvalMetrics,
} from '../types/ragEval';
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
} from './ragEvalFormat';

export default function RagEvalReportPage() {
  const [runs, setRuns] = useState<RagEvalBaseline[]>([]);
  const [selectedRunId, setSelectedRunId] = useState('');
  const [detail, setDetail] = useState<RagEvalBaselineDetail | null>(null);
  const [markdown, setMarkdown] = useState<string | null>(null);
  const [loadingRuns, setLoadingRuns] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [loadingMarkdown, setLoadingMarkdown] = useState(false);
  const [error, setError] = useState('');

  const loadRuns = useCallback(async () => {
    setLoadingRuns(true);
    setError('');
    try {
      const list = await ragEvalApi.baselines();
      setRuns(list);
      // 默认选中最近一次运行，保持与归档列表的时间顺序一致
      setSelectedRunId((current) => current || list[list.length - 1]?.runId || '');
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载评测基线失败');
    } finally {
      setLoadingRuns(false);
    }
  }, []);

  useEffect(() => {
    void loadRuns();
  }, [loadRuns]);

  useEffect(() => {
    if (!selectedRunId) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    setLoadingDetail(true);
    setMarkdown(null);
    ragEvalApi
      .detail(selectedRunId)
      .then((result) => {
        if (!cancelled) {
          setDetail(result);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '加载评测详情失败');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingDetail(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [selectedRunId]);

  const chunkComparison = useMemo(() => buildChunkComparison(runs), [runs]);
  const overall = detail?.summary.overall ?? null;
  const rejection = detail?.summary.rejection ?? null;

  const openMarkdown = async () => {
    if (markdown || !selectedRunId) {
      return;
    }
    setLoadingMarkdown(true);
    try {
      setMarkdown(await ragEvalApi.markdown(selectedRunId));
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载归档报告失败');
    } finally {
      setLoadingMarkdown(false);
    }
  };

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">RAG 检索质量评测</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            固定数据集与固定配置下的可复现评测结果：命中率、证据召回、拒答能力与分块大小对照。
          </p>
        </div>
        <button
          onClick={() => void loadRuns()}
          disabled={loadingRuns}
          className="flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loadingRuns ? 'animate-spin' : ''}`} />
          刷新
        </button>
      </div>

      {error && (
        <div className="mb-6 flex items-center gap-2 px-4 py-3 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm">
          <AlertCircle className="w-4 h-4 shrink-0" />
          {error}
        </div>
      )}

      {loadingRuns && runs.length === 0 ? (
        <div className="flex items-center justify-center min-h-[40vh]">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
        </div>
      ) : runs.length === 0 ? (
        <EmptyState />
      ) : (
        <>
          <div className="flex flex-wrap gap-3 mb-8">
            {runs.map((run) => (
              <button
                key={run.runId}
                onClick={() => setSelectedRunId(run.runId)}
                className={`text-left px-4 py-3 rounded-xl border transition-colors ${
                  run.runId === selectedRunId
                    ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/30'
                    : 'border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                }`}
              >
                <div className="text-sm font-semibold text-slate-800 dark:text-white">
                  {run.label}
                </div>
                <div className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                  {describeRunConfig(run)}
                </div>
                <div className="text-xs text-slate-400 dark:text-slate-500 mt-1">
                  Hit@K {formatPercent(run.overall?.hitAtK ?? null)}
                </div>
              </button>
            ))}
          </div>

          {chunkComparison.length > 1 && (
            <Panel
              title="分块大小对照"
              subtitle="同一数据集、同一模型下只改变生产分块大小，指标统一换算成 0~100 便于横向比较。"
            >
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chunkComparison}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} domain={[0, 100]} />
                    <Tooltip formatter={(value) => `${Number(value)}`} />
                    <Legend />
                    <Bar dataKey="Hit@K" fill="#6366f1" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="证据召回率" fill="#22c55e" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="MRR" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <p className="text-xs text-slate-400 dark:text-slate-500 mt-3">
                MRR 与证据召回率原始取值范围是 0~1，图中已乘 100。结论见仓库根目录的
                <code className="mx-1 px-1 rounded bg-slate-100 dark:bg-slate-700">
                  RAG-EVALUATION.md
                </code>
                。
              </p>
            </Panel>
          )}

          <Panel title="全部归档运行" subtitle="按评测时间升序，最近一次在最下方。">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="text-slate-500 dark:text-slate-400">
                  <tr>
                    <th className="text-left font-medium px-3 py-2">运行</th>
                    <th className="text-left font-medium px-3 py-2">配置</th>
                    <th className="text-right font-medium px-3 py-2">Hit@K</th>
                    <th className="text-right font-medium px-3 py-2">证据召回率</th>
                    <th className="text-right font-medium px-3 py-2">MRR</th>
                    <th className="text-right font-medium px-3 py-2">检索 P50</th>
                    <th className="text-right font-medium px-3 py-2">拒答 F1</th>
                    <th className="text-right font-medium px-3 py-2">坏例</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                  {runs.map((run) => (
                    <tr
                      key={run.runId}
                      onClick={() => setSelectedRunId(run.runId)}
                      className={`cursor-pointer ${
                        run.runId === selectedRunId
                          ? 'bg-primary-50/60 dark:bg-primary-900/20'
                          : 'hover:bg-slate-50 dark:hover:bg-slate-800/60'
                      }`}
                    >
                      <td className="px-3 py-2 font-medium text-slate-800 dark:text-slate-100">
                        {run.label}
                      </td>
                      <td className="px-3 py-2 text-slate-500 dark:text-slate-400">
                        {describeRunConfig(run)}
                      </td>
                      <td className="px-3 py-2 text-right">
                        {formatPercent(run.overall?.hitAtK ?? null)}
                      </td>
                      <td className="px-3 py-2 text-right">
                        {formatRatioAsPercent(run.overall?.evidenceRecallAtK ?? null)}
                      </td>
                      <td className="px-3 py-2 text-right">
                        {formatRatio(run.overall?.mrr ?? null)}
                      </td>
                      <td className="px-3 py-2 text-right">
                        {formatMillis(run.overall?.retrievalMsP50 ?? null)}
                      </td>
                      <td className="px-3 py-2 text-right">
                        {formatRatio(run.rejection?.f1 ?? null)}
                      </td>
                      <td className="px-3 py-2 text-right">{run.badCaseCount ?? MISSING}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>

          {loadingDetail && !detail ? (
            <div className="flex items-center justify-center min-h-[30vh]">
              <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
            </div>
          ) : (
            detail && (
              <>
                <Panel
                  title={`运行详情 · ${detail.summary.label}`}
                  subtitle={`runId ${detail.summary.runId}${
                    detail.summary.timestamp ? ` · ${detail.summary.timestamp}` : ''
                  }`}
                >
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                    <SummaryCard
                      icon={<Target className="w-5 h-5" />}
                      label="Hit@K"
                      value={formatPercent(overall?.hitAtK ?? null)}
                      hint={
                        overall?.inScope === null || overall?.inScope === undefined
                          ? '未统计知识库内样本'
                          : `知识库内 ${overall.inScope} 条样本`
                      }
                    />
                    <SummaryCard
                      icon={<BookOpen className="w-5 h-5" />}
                      label="证据召回率"
                      value={formatRatioAsPercent(overall?.evidenceRecallAtK ?? null)}
                      hint="命中锚点数 / 全部锚点数"
                    />
                    <SummaryCard
                      icon={<Info className="w-5 h-5" />}
                      label="MRR"
                      value={formatRatio(overall?.mrr ?? null)}
                      hint="未命中样本记 0 分"
                    />
                    <SummaryCard
                      icon={<FileText className="w-5 h-5" />}
                      label="检索耗时"
                      value={formatMillis(overall?.retrievalMsP50 ?? null)}
                      hint={`P95 ${formatMillis(overall?.retrievalMsP95 ?? null)}`}
                    />
                  </div>
                </Panel>

                <Panel
                  title="分组指标"
                  subtitle="按数据集切分与题型分别统计，用于定位弱项在哪些题型上。"
                >
                  <MetricsTable groups={detail.groups} />
                </Panel>

                <Panel
                  title="拒答混淆矩阵"
                  subtitle="以数据集的 shouldReject 为实际标签，模型是否给出 NO_RESULT 为预测标签。"
                >
                  {rejection ? (
                    <RejectionMatrix detail={detail} />
                  ) : (
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      该运行没有归档拒答统计。
                    </p>
                  )}
                </Panel>

                <Panel
                  title={`坏例（${detail.badCases.length}）`}
                  subtitle="未达标的样本逐条列出，含实际召回片段与期望证据，便于定位是没召回还是召回错了。"
                >
                  {detail.badCases.length === 0 ? (
                    <p className="text-sm text-slate-500 dark:text-slate-400">该运行没有坏例。</p>
                  ) : (
                    <div className="space-y-4">
                      {detail.badCases.map((badCase, index) => (
                        <BadCaseCard key={`${badCase.id ?? 'case'}-${index}`} badCase={badCase} />
                      ))}
                    </div>
                  )}
                </Panel>

                <Panel title="运行环境" subtitle="两次运行可比的前提是这些值一致。">
                  <EnvironmentSnapshot detail={detail} />
                </Panel>

                {detail.markdownAvailable && (
                  <Panel
                    title="归档原报告"
                    subtitle="Markdown 原文，包含逐样本明细与忠实度复核表。"
                  >
                    {markdown === null ? (
                      <button
                        onClick={() => void openMarkdown()}
                        disabled={loadingMarkdown}
                        className="flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-slate-800 text-white hover:bg-slate-700 disabled:opacity-50"
                      >
                        {loadingMarkdown ? (
                          <Loader2 className="w-4 h-4 animate-spin" />
                        ) : (
                          <FileText className="w-4 h-4" />
                        )}
                        查看 Markdown 原文
                      </button>
                    ) : (
                      <div className="prose prose-slate dark:prose-invert prose-sm max-w-none max-h-[32rem] overflow-y-auto border border-slate-100 dark:border-slate-700 rounded-lg p-4">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{markdown}</ReactMarkdown>
                      </div>
                    )}
                  </Panel>
                )}
              </>
            )
          )}
        </>
      )}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
      <Target className="w-10 h-10 text-slate-300 dark:text-slate-600 mb-4" />
      <p className="text-slate-500 dark:text-slate-400">
        还没有归档的评测基线。运行评测任务后把报告放入
        <code className="mx-1 px-1 rounded bg-slate-100 dark:bg-slate-700">rag-eval/baselines</code>
        即可在此展示。
      </p>
    </div>
  );
}

function Panel({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl p-6 shadow-sm border border-slate-100 dark:border-slate-700 mb-8">
      <div className="mb-4">
        <h2 className="text-base font-semibold text-slate-900 dark:text-white">{title}</h2>
        {subtitle && <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}

function SummaryCard({
  icon,
  label,
  value,
  hint,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  hint: string;
}) {
  return (
    <div className="bg-slate-50 dark:bg-slate-900/40 rounded-xl p-4">
      <div className="flex items-center gap-2 text-slate-400 dark:text-slate-500 mb-2">
        {icon}
        <span className="text-xs">{label}</span>
      </div>
      <div className="text-2xl font-bold text-slate-900 dark:text-white">{value}</div>
      <div className="text-xs text-slate-400 dark:text-slate-500 mt-1">{hint}</div>
    </div>
  );
}

function MetricsTable({
  groups,
}: {
  groups: { group: string; kind: string; metrics: RagEvalMetrics }[];
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="text-slate-500 dark:text-slate-400">
          <tr>
            <th className="text-left font-medium px-3 py-2">分组</th>
            <th className="text-right font-medium px-3 py-2">样本</th>
            <th className="text-right font-medium px-3 py-2">库内</th>
            <th className="text-right font-medium px-3 py-2">Hit@K</th>
            <th className="text-right font-medium px-3 py-2">证据召回率</th>
            <th className="text-right font-medium px-3 py-2">MRR</th>
            <th className="text-right font-medium px-3 py-2">检索 P50</th>
            <th className="text-right font-medium px-3 py-2">检索 P95</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
          {groups.map((group) => (
            <tr key={`${group.kind}-${group.group}`}>
              <td className="px-3 py-2 font-medium text-slate-800 dark:text-slate-100">
                {groupLabel(group.kind, group.group)}
              </td>
              <td className="px-3 py-2 text-right">{group.metrics.samples ?? MISSING}</td>
              <td className="px-3 py-2 text-right">{group.metrics.inScope ?? MISSING}</td>
              <td className="px-3 py-2 text-right">{formatPercent(group.metrics.hitAtK)}</td>
              <td className="px-3 py-2 text-right">
                {formatRatioAsPercent(group.metrics.evidenceRecallAtK)}
              </td>
              <td className="px-3 py-2 text-right">{formatRatio(group.metrics.mrr)}</td>
              <td className="px-3 py-2 text-right">{formatMillis(group.metrics.retrievalMsP50)}</td>
              <td className="px-3 py-2 text-right">{formatMillis(group.metrics.retrievalMsP95)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RejectionMatrix({ detail }: { detail: RagEvalBaselineDetail }) {
  const rejection = detail.summary.rejection;
  if (!rejection) {
    return null;
  }
  const cells = [
    { key: 'tp', label: '该拒且拒了', value: rejection.tp, tone: 'good' },
    { key: 'fn', label: '该拒却答了', value: rejection.fn, tone: 'bad' },
    { key: 'fp', label: '不该拒却拒了', value: rejection.fp, tone: 'warn' },
    { key: 'tn', label: '正常作答', value: rejection.tn, tone: 'good' },
  ];
  const allMissed = (rejection.recall ?? 0) === 0 && (rejection.fn ?? 0) > 0;

  return (
    <div>
      <div className="grid grid-cols-2 gap-3 max-w-lg">
        {cells.map((cell) => (
          <div
            key={cell.key}
            className={`rounded-xl border p-4 ${
              cell.tone === 'bad'
                ? 'border-red-200 dark:border-red-900/50 bg-red-50 dark:bg-red-900/20'
                : 'border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900/40'
            }`}
          >
            <div className="text-2xl font-bold text-slate-900 dark:text-white">
              {cell.value ?? MISSING}
            </div>
            <div className="text-xs text-slate-500 dark:text-slate-400 mt-1">{cell.label}</div>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-x-6 gap-y-2 mt-4 text-sm text-slate-600 dark:text-slate-300">
        <span>Accuracy {formatRatio(rejection.accuracy)}</span>
        <span>Precision {formatRatio(rejection.precision)}</span>
        <span>Recall {formatRatio(rejection.recall)}</span>
        <span>F1 {formatRatio(rejection.f1)}</span>
        <span className="text-slate-400 dark:text-slate-500">
          参与统计 {rejection.samples ?? MISSING} 条生成样本
        </span>
      </div>

      {allMissed && (
        <div className="mt-4 flex items-start gap-2 px-4 py-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300 text-sm">
          <TriangleAlert className="w-4 h-4 shrink-0 mt-0.5" />
          <div>
            知识库外的 {rejection.fn} 条样本全部给出了答案，没有一条被拒答，因此 Recall 与 F1 为
            0。这是当前检索链路的已知短板，不是统计错误。
          </div>
        </div>
      )}
    </div>
  );
}

function BadCaseCard({ badCase }: { badCase: RagEvalBadCase }) {
  return (
    <div className="rounded-xl border border-slate-200 dark:border-slate-700 p-4">
      <div className="flex flex-wrap items-center gap-2 mb-2">
        <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300">
          {badCase.id ?? '未知样本'}
        </span>
        <span className="text-xs px-2 py-0.5 rounded bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400">
          {badCaseReasonLabel(badCase.reason)}
        </span>
        {badCase.tags.map((tag) => (
          <span
            key={tag}
            className="text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400"
          >
            {tag}
          </span>
        ))}
      </div>
      <div className="text-sm font-medium text-slate-800 dark:text-slate-100">
        {badCase.question ?? MISSING}
      </div>
      {badCase.detail && (
        <div className="text-xs text-slate-500 dark:text-slate-400 mt-1">{badCase.detail}</div>
      )}

      {badCase.expectedEvidence.length > 0 && (
        <div className="mt-3">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">
            期望证据
          </div>
          <ul className="text-xs text-slate-600 dark:text-slate-300 space-y-1">
            {badCase.expectedEvidence.map((evidence, index) => (
              <li
                key={index}
                className="pl-3 border-l-2 border-emerald-300 dark:border-emerald-800"
              >
                {evidence}
              </li>
            ))}
          </ul>
        </div>
      )}

      {badCase.topDocs.length > 0 && (
        <div className="mt-3">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">
            实际召回（前 {badCase.topDocs.length} 条）
          </div>
          <ul className="space-y-1">
            {badCase.topDocs.map((doc, index) => (
              <li
                key={`${doc.contentHash ?? index}-${index}`}
                className="text-xs text-slate-600 dark:text-slate-300 pl-3 border-l-2 border-slate-200 dark:border-slate-700"
              >
                <span className="text-slate-400 dark:text-slate-500 mr-1">
                  #{doc.rank ?? index + 1}
                  {doc.score === null ? '' : ` · ${doc.score.toFixed(4)}`}
                </span>
                {doc.excerpt ?? ''}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function EnvironmentSnapshot({ detail }: { detail: RagEvalBaselineDetail }) {
  const env = detail.environment;
  const rows: { label: string; value: string }[] = [
    { label: '对话模型', value: env.model ?? MISSING },
    { label: '向量模型', value: env.embeddingModel ?? MISSING },
    {
      label: '温度',
      value: env.temperature === null ? MISSING : String(env.temperature),
    },
    {
      label: '分块大小',
      value: env.chunkSize === null ? '未记录' : String(env.chunkSize),
    },
    { label: '查询改写', value: flagText(env.rewriteEnabled) },
    { label: '双路融合', value: flagText(env.mergeOriginalQuery) },
    { label: 'Spring AI', value: env.springAiVersion ?? MISSING },
    { label: '数据集哈希', value: env.datasetSha256 ?? MISSING },
  ];

  return (
    <div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2 text-sm max-w-3xl">
        {rows.map((row) => (
          <div
            key={row.label}
            className="flex justify-between gap-4 border-b border-dashed border-slate-100 dark:border-slate-700 py-1"
          >
            <span className="text-slate-500 dark:text-slate-400">{row.label}</span>
            <span className="text-slate-700 dark:text-slate-200 text-right">{row.value}</span>
          </div>
        ))}
      </div>

      {env.resourceFingerprints.length > 0 && (
        <div className="mt-4">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">
            语料与 Prompt 指纹
          </div>
          <ul className="text-xs font-mono text-slate-500 dark:text-slate-400 space-y-0.5">
            {env.resourceFingerprints.map((fingerprint) => (
              <li key={fingerprint}>{fingerprint}</li>
            ))}
          </ul>
        </div>
      )}

      {env.tokenUsageNote && (
        <p className="text-xs text-slate-400 dark:text-slate-500 mt-4">{env.tokenUsageNote}</p>
      )}
    </div>
  );
}

function flagText(value: boolean | null): string {
  if (value === null) {
    return '未记录';
  }
  return value ? '开启' : '关闭';
}
