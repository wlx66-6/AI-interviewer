package ai.interviewer.modules.rageval.dto;

/**
 * 一组样本的检索质量指标。
 *
 * <p>字段全部使用包装类型：归档报告由不同版本的评估器产出，缺失的指标是「未统计」而不是 0，
 * 前端需要区分这两种情况。
 *
 * @param samples 样本总数（含 HARNESS_ERROR）
 * @param inScope 知识库内样本数，Hit@K / MRR / EvidenceRecall@K 的分母
 * @param harnessErrorCount 评测环境自身失败的样本数，单独计数不参与指标
 * @param hitAtK 前 K 个片段中至少命中一个证据锚点的样本占比（百分数）
 * @param mrr 平均倒数排名，未命中记 0
 * @param evidenceRecallAtK 命中锚点数 / 全部锚点数
 */
public record RagEvalMetricsDTO(
    Integer samples,
    Integer inScope,
    Integer harnessErrorCount,
    Double hitAtK,
    Double mrr,
    Double evidenceRecallAtK,
    Long rewriteMsP50,
    Long rewriteMsP95,
    Long retrievalMsP50,
    Long retrievalMsP95,
    Long generationMsP50,
    Long generationMsP95,
    Long endToEndMsP50,
    Long endToEndMsP95) {}
