package ai.interviewer.modules.tokenusage.dto;

/**
 * 单个模型的单价（每千 Token）。
 *
 * @param model 模型名
 * @param inputPer1k 每千输入 Token 单价
 * @param outputPer1k 每千输出 Token 单价
 */
public record ModelPriceDTO(String model, Double inputPer1k, Double outputPer1k) {}
