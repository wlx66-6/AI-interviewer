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
import { AlertCircle, Coins, Gauge, Info, Loader2, RefreshCw, Wallet, Zap } from 'lucide-react';
import { tokenUsageApi } from '../api/tokenUsage';
import type { ModelPricing, TokenUsageGroupBy, TokenUsageStats } from '../types/tokenUsage';
import {
  COST_UNKNOWN_LABEL,
  buildTokenChartData,
  failureRate,
  formatCost,
  formatLatency,
  formatPercent,
  formatTokenCount,
} from './tokenUsageFormat';

type RangePreset = 'today' | '7d' | '30d';

const RANGE_OPTIONS: { value: RangePreset; label: string; backDays: number }[] = [
  { value: 'today', label: '今天', backDays: 0 },
  { value: '7d', label: '近 7 天', backDays: 6 },
  { value: '30d', label: '近 30 天', backDays: 29 },
];

const GROUP_OPTIONS: { value: TokenUsageGroupBy; label: string }[] = [
  { value: 'model', label: '按模型' },
  { value: 'source', label: '按调用来源' },
  { value: 'kind', label: '按调用类型' },
  { value: 'provider', label: '按 Provider' },
];

const SOURCE_LABELS: Record<string, string> = {
  default: '默认对话',
  plain: '轻量对话',
  voice: '语音面试',
  embedding: '向量化',
};

const KIND_LABELS: Record<string, string> = {
  CHAT: '对话',
  EMBEDDING: '向量化',
};

function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function rangeParams(preset: RangePreset): { from: string; to: string } {
  const option = RANGE_OPTIONS.find((item) => item.value === preset) ?? RANGE_OPTIONS[1];
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - option.backDays);
  return { from: toIsoDate(from), to: toIsoDate(to) };
}

export default function TokenUsagePage() {
  const [range, setRange] = useState<RangePreset>('7d');
  const [groupBy, setGroupBy] = useState<TokenUsageGroupBy>('model');
  const [stats, setStats] = useState<TokenUsageStats | null>(null);
  const [pricing, setPricing] = useState<ModelPricing | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const { from, to } = rangeParams(range);
      const [statsResult, pricingResult] = await Promise.all([
        tokenUsageApi.stats({ from, to, groupBy }),
        tokenUsageApi.pricing(),
      ]);
      setStats(statsResult);
      setPricing(pricingResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载 Token 用量失败');
    } finally {
      setLoading(false);
    }
  }, [range, groupBy]);

  useEffect(() => {
    void load();
  }, [load]);

  const chartData = useMemo(() => buildTokenChartData(stats?.rows ?? []), [stats]);

  const failureRatio = stats ? failureRate({ calls: stats.calls, failures: stats.failures }) : 0;
  const hasPricing = (pricing?.models.length ?? 0) > 0;

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">Token 用量统计</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            统计每次 LLM 调用消耗的 Token、时延与预估成本，用于观察模型开销分布。
          </p>
        </div>
        <button
          onClick={() => void load()}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-2 text-sm rounded-lg border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          刷新
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-6">
        <div className="flex rounded-lg border border-slate-200 dark:border-slate-700 overflow-hidden">
          {RANGE_OPTIONS.map((option) => (
            <button
              key={option.value}
              onClick={() => setRange(option.value)}
              className={`px-4 py-2 text-sm transition-colors ${
                range === option.value
                  ? 'bg-primary-500 text-white'
                  : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="flex rounded-lg border border-slate-200 dark:border-slate-700 overflow-hidden">
          {GROUP_OPTIONS.map((option) => (
            <button
              key={option.value}
              onClick={() => setGroupBy(option.value)}
              className={`px-4 py-2 text-sm transition-colors ${
                groupBy === option.value
                  ? 'bg-slate-800 text-white dark:bg-slate-600'
                  : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        {stats && (
          <span className="text-xs text-slate-400 dark:text-slate-500">
            统计区间 {stats.from} ~ {stats.to}
          </span>
        )}
      </div>

      {error && (
        <div className="mb-6 flex items-center gap-2 px-4 py-3 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm">
          <AlertCircle className="w-4 h-4 shrink-0" />
          {error}
        </div>
      )}

      {loading && !stats ? (
        <div className="flex items-center justify-center min-h-[40vh]">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
        </div>
      ) : stats ? (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
            <SummaryCard
              icon={<Zap className="w-5 h-5" />}
              label="总 Token"
              value={formatTokenCount(stats.totalTokens)}
              hint={`输入 ${formatTokenCount(stats.promptTokens)} · 输出 ${formatTokenCount(
                stats.completionTokens,
              )}`}
            />
            <SummaryCard
              icon={<Gauge className="w-5 h-5" />}
              label="调用次数"
              value={String(stats.calls)}
              hint={`失败 ${stats.failures} 次（${formatPercent(failureRatio)}）`}
            />
            <SummaryCard
              icon={<Coins className="w-5 h-5" />}
              label="预估成本"
              value={formatCost(stats.totalCost, stats.currency, hasPricing)}
              hint={
                hasPricing
                  ? stats.costComplete
                    ? `币种 ${stats.currency}`
                    : '部分模型未配置单价，金额偏小'
                  : COST_UNKNOWN_LABEL
              }
            />
            <SummaryCard
              icon={<Wallet className="w-5 h-5" />}
              label="分组数量"
              value={String(stats.rows.length)}
              hint={GROUP_OPTIONS.find((item) => item.value === stats.groupBy)?.label ?? ''}
            />
          </div>

          {!hasPricing && (
            <div className="mb-8 flex items-start gap-2 px-4 py-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300 text-sm">
              <Info className="w-4 h-4 shrink-0 mt-0.5" />
              <div>
                尚未配置模型单价，因此只统计 Token 用量、不折算金额。在
                <code className="mx-1 px-1 rounded bg-amber-100 dark:bg-amber-900/40">
                  application.yml
                </code>
                的
                <code className="mx-1 px-1 rounded bg-amber-100 dark:bg-amber-900/40">
                  app.ai.pricing.models
                </code>
                下按官方价目填入每千 Token 单价后即可看到成本。
              </div>
            </div>
          )}

          <div className="bg-white dark:bg-slate-800 rounded-xl p-6 shadow-sm border border-slate-100 dark:border-slate-700 mb-8">
            <h2 className="text-base font-semibold text-slate-900 dark:text-white mb-4">
              Token 分布（Top {chartData.length}）
            </h2>
            {chartData.length === 0 ? (
              <p className="text-sm text-slate-500 dark:text-slate-400 py-8 text-center">
                该时间窗内还没有调用记录。
              </p>
            ) : (
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} tickFormatter={formatTokenCount} />
                    <Tooltip formatter={(value) => formatTokenCount(Number(value))} />
                    <Legend />
                    <Bar dataKey="输入" stackId="tokens" fill="#6366f1" radius={[0, 0, 0, 0]} />
                    <Bar dataKey="输出" stackId="tokens" fill="#22c55e" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>

          <div className="bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-100 dark:border-slate-700 overflow-hidden mb-8">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 dark:bg-slate-900/40 text-slate-500 dark:text-slate-400">
                <tr>
                  <th className="text-left font-medium px-4 py-3">分组</th>
                  <th className="text-right font-medium px-4 py-3">输入</th>
                  <th className="text-right font-medium px-4 py-3">输出</th>
                  <th className="text-right font-medium px-4 py-3">总 Token</th>
                  <th className="text-right font-medium px-4 py-3">调用</th>
                  <th className="text-right font-medium px-4 py-3">失败</th>
                  <th className="text-right font-medium px-4 py-3">平均时延</th>
                  <th className="text-right font-medium px-4 py-3">成本</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {stats.rows.length === 0 ? (
                  <tr>
                    <td
                      colSpan={8}
                      className="px-4 py-10 text-center text-slate-500 dark:text-slate-400"
                    >
                      该时间窗内还没有调用记录。
                    </td>
                  </tr>
                ) : (
                  stats.rows.map((row) => (
                    <tr key={row.key} className="text-slate-700 dark:text-slate-200">
                      <td className="px-4 py-3">
                        <div className="font-medium">{row.key}</div>
                        <div className="text-xs text-slate-400 dark:text-slate-500">
                          {describeRow(row.kind, row.source)}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-right">{formatTokenCount(row.promptTokens)}</td>
                      <td className="px-4 py-3 text-right">
                        {formatTokenCount(row.completionTokens)}
                      </td>
                      <td className="px-4 py-3 text-right font-medium">
                        {formatTokenCount(row.totalTokens)}
                      </td>
                      <td className="px-4 py-3 text-right">{row.calls}</td>
                      <td className="px-4 py-3 text-right">
                        {row.failures > 0 ? (
                          <span className="text-red-500">{row.failures}</span>
                        ) : (
                          row.failures
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">{formatLatency(row.avgLatencyMs)}</td>
                      <td className="px-4 py-3 text-right">
                        {row.priced ? (
                          formatCost(row.cost, stats.currency, true)
                        ) : (
                          <span className="text-xs text-slate-400">{COST_UNKNOWN_LABEL}</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="bg-white dark:bg-slate-800 rounded-xl p-6 shadow-sm border border-slate-100 dark:border-slate-700">
            <h2 className="text-base font-semibold text-slate-900 dark:text-white mb-4">
              已配置单价
            </h2>
            {pricing && pricing.models.length > 0 ? (
              <ul className="text-sm text-slate-600 dark:text-slate-300 space-y-2">
                {pricing.models.map((model) => (
                  <li key={model.model} className="flex items-center justify-between">
                    <span className="font-medium">{model.model}</span>
                    <span className="text-slate-500 dark:text-slate-400">
                      输入 {model.inputPer1k ?? '-'} / 千 · 输出 {model.outputPer1k ?? '-'} / 千（
                      {pricing.currency}）
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-slate-500 dark:text-slate-400">
                暂无单价配置。单价是「每千 Token
                的价格」，不同厂商、不同版本差异很大，因此不内置默认值。
              </p>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}

function describeRow(kind: string, source: string): string {
  const kindLabel = KIND_LABELS[kind] ?? kind;
  const sourceLabel = SOURCE_LABELS[source] ?? source;
  return `${kindLabel} · ${sourceLabel}`;
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
    <div className="bg-white dark:bg-slate-800 rounded-xl p-5 shadow-sm border border-slate-100 dark:border-slate-700">
      <div className="flex items-center gap-2 text-slate-400 dark:text-slate-500 mb-2">
        {icon}
        <span className="text-xs">{label}</span>
      </div>
      <div className="text-2xl font-bold text-slate-900 dark:text-white">{value}</div>
      <div className="text-xs text-slate-400 dark:text-slate-500 mt-1">{hint}</div>
    </div>
  );
}
