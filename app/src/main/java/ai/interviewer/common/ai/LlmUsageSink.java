package ai.interviewer.common.ai;

/**
 * LLM 用量接收端（SPI）。
 *
 * <p>{@code common/ai} 只负责「拦截并上报」，不关心用量如何存储与展示；具体落库、聚合、计价由业务模块
 * 实现本接口注入回来。这样依赖方向保持为 {@code modules -> common}，不会出现公共层反向依赖业务模块。
 *
 * <p>实现方必须保证：<b>本方法永不抛异常影响调用方</b>。用量统计属于旁路能力，任何失败都只能记录日志。
 */
public interface LlmUsageSink {

  /** 调用类别：对话。 */
  String KIND_CHAT = "CHAT";
  /** 调用类别：向量化。 */
  String KIND_EMBEDDING = "EMBEDDING";
  /** 来源：默认 ChatClient（带工具与记忆）。 */
  String SOURCE_DEFAULT = "default";
  /** 来源：轻量 ChatClient（仅安全护栏）。 */
  String SOURCE_PLAIN = "plain";
  /** 来源：语音面试 ChatClient（流式 + 技能工具）。 */
  String SOURCE_VOICE = "voice";
  /** 来源：Embedding 模型。 */
  String SOURCE_EMBEDDING = "embedding";

  /**
   * 记录一次调用用量。
   *
   * @param usage 用量快照；失败调用中无法取得的字段为 0
   */
  void record(LlmUsage usage);

  /**
   * 单次调用的用量快照。
   *
   * @param providerId 模型提供方 ID，未知时为 {@code default}
   * @param model 模型名，可能为 {@code null}
   * @param kind 调用类别，见 {@link #KIND_CHAT} / {@link #KIND_EMBEDDING}
   * @param source 调用来源，见 {@code SOURCE_*} 常量
   * @param promptTokens 输入 Token 数
   * @param completionTokens 输出 Token 数
   * @param totalTokens 总 Token 数
   * @param latencyMs 端到端时延（毫秒）
   * @param success 调用是否成功
   */
  record LlmUsage(
      String providerId,
      String model,
      String kind,
      String source,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      long latencyMs,
      boolean success) {

    /** 调用失败、无法取得 usage 时的占位快照。 */
    public static LlmUsage failure(String providerId, String kind, String source, long latencyMs) {
      return new LlmUsage(providerId, null, kind, source, 0, 0, 0, latencyMs, false);
    }
  }
}
