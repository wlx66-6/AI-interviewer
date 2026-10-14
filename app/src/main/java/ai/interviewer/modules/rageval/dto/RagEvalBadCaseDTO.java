package ai.interviewer.modules.rageval.dto;

import java.util.List;

/**
 * 坏例：未达标的样本，逐条记录失败原因以便定位检索链路问题。
 *
 * @param reason 失败归类，取值 {@code NO_EVIDENCE_HIT} / {@code LOW_FIRST_RANK} /
 *     {@code EVIDENCE_INCOMPLETE} / {@code OOS_NOT_REJECTED}
 * @param topDocs 实际召回的片段，用于人工判断是「没召回」还是「召回错了」
 * @param expectedEvidence 该题标注的期望证据锚点文本
 */
public record RagEvalBadCaseDTO(
    String id,
    String question,
    List<String> tags,
    String reason,
    String detail,
    String answerExcerpt,
    List<RagEvalDocDTO> topDocs,
    List<String> expectedEvidence) {}
