package ai.interviewer.modules.tokenusage.dto;

import java.util.List;

/**
 * 已配置的模型单价表，供前端说明「金额按什么口径算出来的」。
 *
 * @param currency 币种
 * @param models 已配置单价的模型列表
 */
public record ModelPricingResponse(String currency, List<ModelPriceDTO> models) {}
