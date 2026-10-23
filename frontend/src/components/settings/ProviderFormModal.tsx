import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronDown, Eye, EyeOff, Loader2 } from 'lucide-react';
import { llmProviderApi } from '../../api/llmProvider';
import type {
  CreateProviderRequest,
  ProviderItem,
  UpdateProviderRequest,
} from '../../types/llmProvider';

// Provider 预设：已知 Provider 的 Base URL、推荐模型和向量模型
const PROVIDER_PRESETS: Record<
  string,
  {
    baseUrl: string;
    models: { value: string; label: string }[];
    embeddingModels?: { value: string; label: string }[];
    embeddingDimensions?: number;
    supportsEmbedding: boolean;
  }
> = {
  dashscope: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: [
      { value: 'qwen3.8-flash', label: 'Qwen3.8 Flash — 默认·性价比首选' },
      { value: 'qwen3.7-flash', label: 'Qwen3.7 Flash — 上一代 Flash' },
      { value: 'qwen3.6-flash', label: 'Qwen3.6 Flash — 老一代 Flash' },
      { value: 'qwen3.6-plus', label: 'Qwen3.6 Plus — 高性能' },
      { value: 'qwen-max', label: 'Qwen Max — 稳定旗舰' },
      { value: 'qwen-plus', label: 'Qwen Plus — 均衡' },
      { value: 'qwen-flash', label: 'Qwen Flash — 经济' },
    ],
    embeddingModels: [{ value: 'text-embedding-v3', label: 'text-embedding-v3 — 推荐' }],
    embeddingDimensions: 1024,
    supportsEmbedding: true,
  },
  // deepseek / kimi / glm 预设的 Base URL 也指向百炼：百炼托管的第三方模型走同一个
  // OpenAI 兼容端点，一把百炼 Key 就能切换多家模型。想直连原厂时把 Base URL
  // 改成 api.deepseek.com / api.moonshot.cn / open.bigmodel.cn 并填各自 Key 即可。
  deepseek: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: [
      { value: 'deepseek-v4.1-flash', label: 'DeepSeek V4.1 Flash — 百炼托管·性价比' },
      { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro — 百炼托管·最强推理' },
    ],
    supportsEmbedding: false,
  },
  kimi: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: [
      { value: 'kimi-k3', label: 'Kimi K3 — 百炼托管·默认·最新' },
      { value: 'kimi-k2.6', label: 'Kimi K2.6 — 百炼托管·上一代' },
      { value: 'kimi-k2.5', label: 'Kimi K2.5 — 百炼托管·老一代' },
    ],
    supportsEmbedding: false,
  },
  glm: {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: [
      { value: 'glm-5.3', label: 'GLM-5.3 — 百炼托管·最新' },
      { value: 'glm-5', label: 'GLM-5 — 百炼托管·稳定' },
    ],
    supportsEmbedding: false,
  },
};

const INPUT_CLASS = `w-full px-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600
  bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white
  placeholder:text-slate-400 focus:outline-none focus:ring-2
  focus:ring-primary-500/50 focus:border-primary-400 transition-shadow`;

type ProviderFormModalProps = {
  /** null 表示新增，否则为编辑目标 */
  provider: ProviderItem | null;
  onClose: () => void;
  onSaved: (message: string) => void;
  onError: (message: string) => void;
};

/**
 * 新增 / 编辑 Provider 的弹窗。
 *
 * 表单状态留在这里而不是页面上：这些字段只在弹窗打开期间有意义，放在页面上会让
 * SettingsPage 的状态清单再长十几个并且互相没有关系。
 */
export default function ProviderFormModal({
  provider,
  onClose,
  onSaved,
  onError,
}: ProviderFormModalProps) {
  const isEditing = provider !== null;

  const [formId, setFormId] = useState(provider?.id ?? '');
  const [formBaseUrl, setFormBaseUrl] = useState(provider?.baseUrl ?? '');
  const [formApiKey, setFormApiKey] = useState('');
  const [formModel, setFormModel] = useState(provider?.model ?? '');
  const [formEmbeddingModel, setFormEmbeddingModel] = useState(provider?.embeddingModel ?? '');
  const [formEmbeddingDimensions, setFormEmbeddingDimensions] = useState(
    provider?.embeddingDimensions != null ? String(provider.embeddingDimensions) : '1024',
  );
  const [formSupportsEmbedding, setFormSupportsEmbedding] = useState(
    provider?.supportsEmbedding ?? false,
  );
  const [formTemperature, setFormTemperature] = useState(
    provider?.temperature != null ? String(provider.temperature) : '',
  );
  const [showApiKey, setShowApiKey] = useState(false);
  const [showModelDropdown, setShowModelDropdown] = useState(false);
  const [showEmbeddingDropdown, setShowEmbeddingDropdown] = useState(false);
  const [saving, setSaving] = useState(false);

  const currentPreset = useMemo(() => PROVIDER_PRESETS[formId.toLowerCase()], [formId]);

  /** 校验通过时返回向量维度，否则弹提示并返回 null */
  const resolveEmbeddingDimensions = (): number | null => {
    if (!formSupportsEmbedding) {
      return 1024;
    }
    if (!formEmbeddingModel.trim()) {
      onError('支持向量化时需要填写向量模型，例如 text-embedding-v3');
      return null;
    }
    const parsed = parseInt(formEmbeddingDimensions.trim(), 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      onError('向量维度必须为正整数，当前 pgvector 表为 1024 维');
      return null;
    }
    return parsed;
  };

  const handleIdChange = (value: string) => {
    setFormId(value);
    // 新建时自动填充已知 Provider 的 Base URL
    if (isEditing) {
      return;
    }
    const preset = PROVIDER_PRESETS[value.toLowerCase()];
    if (preset) {
      setFormBaseUrl(preset.baseUrl);
      setFormSupportsEmbedding(preset.supportsEmbedding);
      setFormEmbeddingModel(preset.embeddingModels?.[0]?.value ?? '');
      setFormEmbeddingDimensions(String(preset.embeddingDimensions ?? 1024));
    }
  };

  const handleSave = async () => {
    if (!isEditing) {
      if (!formId.trim() || !formBaseUrl.trim() || !formApiKey.trim() || !formModel.trim()) {
        onError('请填写必填字段');
        return;
      }
    } else if (!formBaseUrl.trim() || !formModel.trim()) {
      onError('请填写必填字段');
      return;
    }

    const embeddingDimensions = resolveEmbeddingDimensions();
    if (embeddingDimensions === null) {
      return;
    }

    setSaving(true);
    try {
      if (isEditing) {
        const data: UpdateProviderRequest = {
          baseUrl: formBaseUrl.trim(),
          model: formModel.trim(),
          embeddingModel: formEmbeddingModel.trim(),
          supportsEmbedding: formSupportsEmbedding,
        };
        if (formSupportsEmbedding) {
          data.embeddingDimensions = embeddingDimensions;
        }
        if (formApiKey.trim()) {
          data.apiKey = formApiKey.trim();
        }
        if (formTemperature.trim()) {
          const temp = parseFloat(formTemperature.trim());
          if (!isNaN(temp)) data.temperature = temp;
        }
        await llmProviderApi.update(provider.id, data);
        onSaved('Provider 更新成功');
      } else {
        const data: CreateProviderRequest = {
          id: formId.trim(),
          baseUrl: formBaseUrl.trim(),
          apiKey: formApiKey.trim(),
          model: formModel.trim(),
          supportsEmbedding: formSupportsEmbedding,
        };
        if (formEmbeddingModel.trim()) {
          data.embeddingModel = formEmbeddingModel.trim();
          data.embeddingDimensions = embeddingDimensions;
        }
        if (formTemperature.trim()) {
          const temp = parseFloat(formTemperature.trim());
          if (!isNaN(temp)) data.temperature = temp;
        }
        await llmProviderApi.create(data);
        onSaved('Provider 创建成功');
      }
      onClose();
    } catch (err) {
      console.error('Failed to save provider:', err);
      onError(err instanceof Error ? err.message : isEditing ? '更新失败' : '创建失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
        className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
      />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 20 }}
          onClick={(e) => e.stopPropagation()}
          className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-lg w-full p-6"
        >
          <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-5">
            {isEditing ? '编辑 Provider' : '新增 Provider'}
          </h3>

          <div className="space-y-4">
            {/* Provider ID */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Provider ID <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={formId}
                onChange={(e) => handleIdChange(e.target.value)}
                disabled={isEditing}
                placeholder="例如: dashscope, deepseek, kimi, glm"
                className={`${INPUT_CLASS} disabled:opacity-50 disabled:cursor-not-allowed`}
              />
            </div>

            {/* Base URL */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Base URL <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={formBaseUrl}
                onChange={(e) => setFormBaseUrl(e.target.value)}
                placeholder="例如: https://api.openai.com/v1"
                className={INPUT_CLASS}
              />
            </div>

            {/* API Key */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                API Key{' '}
                {isEditing && <span className="text-slate-400 font-normal">(留空则不修改)</span>}
                {!isEditing && <span className="text-red-500">*</span>}
              </label>
              <div className="relative">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  value={formApiKey}
                  onChange={(e) => setFormApiKey(e.target.value)}
                  placeholder={isEditing ? '留空则保持原值' : '输入 API Key'}
                  className={`${INPUT_CLASS} pr-10`}
                />
                <button
                  type="button"
                  onClick={() => setShowApiKey(!showApiKey)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400
                    hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                >
                  {showApiKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* Chat Model */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                聊天模型 <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={formModel}
                  onChange={(e) => {
                    setFormModel(e.target.value);
                    setShowModelDropdown(false);
                  }}
                  onFocus={() => currentPreset && setShowModelDropdown(true)}
                  onBlur={() => setTimeout(() => setShowModelDropdown(false), 150)}
                  placeholder={
                    currentPreset
                      ? '从下拉列表选择或输入自定义聊天模型名'
                      : '例如: qwen3.8-flash, deepseek-v4.1-flash, kimi-k3'
                  }
                  className={INPUT_CLASS}
                />
                {currentPreset && (
                  <button
                    type="button"
                    onClick={() => setShowModelDropdown(!showModelDropdown)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400
                      hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                  >
                    <ChevronDown className="w-4 h-4" />
                  </button>
                )}
                {showModelDropdown && currentPreset && (
                  <div
                    className="absolute z-10 mt-1 w-full bg-white dark:bg-slate-700
                      border border-slate-200 dark:border-slate-600 rounded-xl shadow-lg
                      max-h-60 overflow-auto"
                  >
                    {currentPreset.models.map((m) => (
                      <button
                        key={m.value}
                        type="button"
                        onClick={() => {
                          setFormModel(m.value);
                          setShowModelDropdown(false);
                        }}
                        className={`w-full px-4 py-2.5 text-left text-sm hover:bg-primary-50
                          dark:hover:bg-slate-600 transition-colors flex justify-between items-center
                          ${
                            formModel === m.value
                              ? 'text-primary-600 dark:text-primary-400 font-medium bg-primary-50 dark:bg-slate-600'
                              : 'text-slate-700 dark:text-slate-200'
                          }`}
                      >
                        <span className="font-mono">{m.value}</span>
                        <span className="text-xs text-slate-400 dark:text-slate-500 ml-2 whitespace-nowrap">
                          {m.label}
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Embedding Model */}
            <div>
              <div className="mb-1.5 flex items-center justify-between gap-3">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                  向量模型{' '}
                  <span className="text-slate-400 font-normal">
                    (知识库向量化，例如 text-embedding-v3)
                  </span>
                </label>
                <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600 dark:text-slate-300">
                  <input
                    type="checkbox"
                    checked={formSupportsEmbedding}
                    onChange={(e) => {
                      setFormSupportsEmbedding(e.target.checked);
                      if (!e.target.checked) {
                        setFormEmbeddingModel('');
                        setFormEmbeddingDimensions('1024');
                      }
                    }}
                    className="h-4 w-4 rounded border-slate-300 text-primary-600 focus:ring-primary-500"
                  />
                  支持 Embedding
                </label>
              </div>
              <div className="relative">
                <input
                  type="text"
                  value={formEmbeddingModel}
                  onChange={(e) => {
                    setFormEmbeddingModel(e.target.value);
                    setShowEmbeddingDropdown(false);
                  }}
                  onFocus={() =>
                    formSupportsEmbedding &&
                    currentPreset?.embeddingModels &&
                    setShowEmbeddingDropdown(true)
                  }
                  onBlur={() => setTimeout(() => setShowEmbeddingDropdown(false), 150)}
                  disabled={!formSupportsEmbedding}
                  placeholder={
                    formSupportsEmbedding
                      ? currentPreset?.embeddingModels
                        ? '从下拉列表选择或输入自定义向量模型名'
                        : '例如: text-embedding-v3'
                      : 'DeepSeek / Kimi 等 Provider 通常不支持 Embedding'
                  }
                  className={`${INPUT_CLASS} disabled:cursor-not-allowed disabled:opacity-60`}
                />
                {formSupportsEmbedding && currentPreset?.embeddingModels && (
                  <button
                    type="button"
                    onClick={() => setShowEmbeddingDropdown(!showEmbeddingDropdown)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400
                      hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                  >
                    <ChevronDown className="w-4 h-4" />
                  </button>
                )}
                {formSupportsEmbedding &&
                  showEmbeddingDropdown &&
                  currentPreset?.embeddingModels && (
                    <div
                      className="absolute z-10 mt-1 w-full bg-white dark:bg-slate-700
                      border border-slate-200 dark:border-slate-600 rounded-xl shadow-lg
                      max-h-60 overflow-auto"
                    >
                      {currentPreset.embeddingModels.map((m) => (
                        <button
                          key={m.value}
                          type="button"
                          onClick={() => {
                            setFormEmbeddingModel(m.value);
                            setShowEmbeddingDropdown(false);
                          }}
                          className={`w-full px-4 py-2.5 text-left text-sm hover:bg-primary-50
                          dark:hover:bg-slate-600 transition-colors flex justify-between items-center
                          ${
                            formEmbeddingModel === m.value
                              ? 'text-primary-600 dark:text-primary-400 font-medium bg-primary-50 dark:bg-slate-600'
                              : 'text-slate-700 dark:text-slate-200'
                          }`}
                        >
                          <span className="font-mono">{m.value}</span>
                          <span className="text-xs text-slate-400 dark:text-slate-500 ml-2 whitespace-nowrap">
                            {m.label}
                          </span>
                        </button>
                      ))}
                    </div>
                  )}
              </div>
            </div>

            {formSupportsEmbedding && (
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                  向量维度{' '}
                  <span className="text-slate-400 font-normal">
                    (必须与 pgvector 表一致，当前为 1024)
                  </span>
                </label>
                <input
                  type="number"
                  min={1}
                  value={formEmbeddingDimensions}
                  onChange={(e) => setFormEmbeddingDimensions(e.target.value)}
                  placeholder="1024"
                  className={INPUT_CLASS}
                />
              </div>
            )}

            {/* Temperature */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Temperature <span className="text-slate-400 font-normal">(可选, 默认 0.2)</span>
              </label>
              <input
                type="text"
                value={formTemperature}
                onChange={(e) => setFormTemperature(e.target.value)}
                placeholder="例如: 0.2, 0.7, 1"
                className={INPUT_CLASS}
              />
            </div>
          </div>

          {/* Modal actions */}
          <div className="flex gap-3 justify-end mt-6">
            <motion.button
              onClick={onClose}
              disabled={saving}
              className="px-5 py-2.5 border border-slate-200 dark:border-slate-600
                text-slate-600 dark:text-slate-300 rounded-xl font-medium text-sm
                hover:bg-slate-50 dark:hover:bg-slate-700 transition-all
                disabled:opacity-50 disabled:cursor-not-allowed"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              取消
            </motion.button>
            <motion.button
              onClick={handleSave}
              disabled={saving}
              className="px-5 py-2.5 text-white rounded-xl font-semibold text-sm
                bg-gradient-to-r from-primary-500 to-primary-600
                shadow-lg shadow-primary-500/25
                hover:from-primary-600 hover:to-primary-700
                transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {saving ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  保存中...
                </span>
              ) : (
                '保存'
              )}
            </motion.button>
          </div>
        </motion.div>
      </div>
    </>
  );
}
