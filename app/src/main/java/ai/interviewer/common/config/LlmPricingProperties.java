package ai.interviewer.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import lombok.Data;

/**
 * 模型计价配置，用于把 Token 用量换算成金额。
 *
 * <p>单价表默认为空：不同厂商、同一厂商不同版本的价格差异很大，且会随时调整，因此这里<b>不内置任何猜测值</b>。
 * 未配置单价的模型在统计接口里成本返回 {@code null} 而不是 0——「不知道」和「免费」是两件事。
 *
 * <pre>
 * app:
 *   ai:
 *     pricing:
 *       currency: CNY
 *       models:
 *         &lt;模型名&gt;:
 *           input-per-1k: 0.004
 *           output-per-1k: 0.012
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.pricing")
public class LlmPricingProperties {

  /** 计价币种，仅用于前端展示。 */
  private String currency = "CNY";

  /** 模型名到单价的映射，模型名匹配忽略大小写与首尾空格。 */
  private Map<String, ModelPrice> models = new LinkedHashMap<>();

  /** 查找模型单价，未配置时返回空。 */
  public Optional<ModelPrice> find(String model) {
    if (model == null || model.isBlank() || models.isEmpty()) {
      return Optional.empty();
    }
    ModelPrice exact = models.get(model);
    if (exact != null) {
      return Optional.of(exact);
    }
    return models.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(model.trim()))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  /** 单个模型的单价（每千 Token）。 */
  @Data
  public static class ModelPrice {

    /** 每千输入 Token 单价，未配置视为不可计价。 */
    private Double inputPer1k;

    /** 每千输出 Token 单价，未配置视为不可计价。 */
    private Double outputPer1k;
  }
}
