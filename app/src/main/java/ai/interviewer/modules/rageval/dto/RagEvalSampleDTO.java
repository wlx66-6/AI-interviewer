package ai.interviewer.modules.rageval.dto;

import java.util.List;

/**
 * 逐样本的检索结果明细，用于在页面上核对单题表现。
 *
 * <p>不包含模型生成的完整答案：报告页展示的是检索质量，答案正文已经在归档 Markdown 里。
 *
 * @param split 数据集切分：{@code dev}（调参用）/ {@code holdout}（仅终评）
 * @param outcome 执行结果：{@code RETRIEVED} 仅检索 / {@code ANSWERED} 已作答 /
 *     {@code NO_RESULT} 拒答 / {@code HARNESS_ERROR} 环境失败
 * @param hit 是否命中任一期望证据锚点
 * @param firstHitRank 命中证据的最小排名，未命中为 null
 */
public record RagEvalSampleDTO(
    String id,
    String split,
    List<String> tags,
    String outcome,
    Boolean hit,
    Integer firstHitRank,
    Double evidenceRecall,
    Long retrievalMs,
    Long generationMs,
    Long totalMs,
    Boolean shouldReject,
    Boolean evaluateGeneration,
    Boolean predictedReject) {}
