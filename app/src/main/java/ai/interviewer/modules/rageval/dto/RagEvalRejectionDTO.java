package ai.interviewer.modules.rageval.dto;

/**
 * 知识库外问题的拒答混淆矩阵。
 *
 * <p>以数据集的 {@code shouldReject} 为实际标签、模型是否给出 {@code NO_RESULT} 为预测标签：
 * {@code tp} 该拒且拒了，{@code fn} 该拒却答了，{@code fp} 不该拒却拒了，{@code tn} 正常作答。
 *
 * @param samples 参与统计的生成样本数，等于 tp + fn + fp + tn
 */
public record RagEvalRejectionDTO(
    Integer tp,
    Integer fn,
    Integer fp,
    Integer tn,
    Integer samples,
    Double accuracy,
    Double precision,
    Double recall,
    Double f1) {}
