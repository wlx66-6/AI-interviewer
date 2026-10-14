package ai.interviewer.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产配置绑定冒烟测试：只加载真实的 application.yml 并绑定纯配置类，不依赖数据库/Redis。
 *
 * <p>为什么需要它：默认的 {@code ./gradlew :app:test} 会排除所有需要真实基础设施的标签，
 * 于是"能启动完整应用上下文"这条路径从未被测过。改造过程中就因此漏掉了一个只在真实启动时
 * 才暴露的问题——{@code app.ai.pricing.models: {}} 这种空的 YAML flow map 会被 Spring Boot
 * 摊平成标量空串 {@code app.ai.pricing.models=""}，再绑定到 {@code Map<String, ModelPrice>}
 * 时抛 {@code ConverterNotFoundException}，应用启动直接失败，而 535 个用例全是绿的。
 */
@DisplayName("生产配置 application.yml 可完整绑定")
class ApplicationYamlBindingTest {

  /**
   * application.yml 里唯一没有默认值的占位符是 {@code ${AI_BAILIAN_API_KEY}}，这里给个假值。
   */
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(BindingConfiguration.class)
      .withPropertyValues("AI_BAILIAN_API_KEY=test-key")
      .withInitializer(context -> loadApplicationYaml()
          .forEach(source -> context.getEnvironment().getPropertySources().addLast(source)));

  private static List<PropertySource<?>> loadApplicationYaml() {
    try {
      return new YamlPropertySourceLoader()
          .load("application", new ClassPathResource("application.yml"));
    } catch (IOException e) {
      throw new UncheckedIOException("无法加载 application.yml", e);
    }
  }

  @Test
  @DisplayName("application.yml 能绑定到全部纯配置类，不因空 Map 等写法启动失败")
  void shouldBindProductionYaml() {
    contextRunner.run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  @DisplayName("未配置单价时计价表绑定为空 map，而不是空串绑定失败")
  void shouldBindEmptyPricingModels() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context.getBean(LlmPricingProperties.class).getModels()).isEmpty();
      assertThat(context.getBean(LlmPricingProperties.class).getCurrency()).isEqualTo("CNY");
    });
  }

  @Test
  @DisplayName("多 Provider 配置能绑定成非空映射")
  void shouldBindProviders() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context.getBean(LlmProviderProperties.class).getProviders())
          .containsKey("dashscope");
    });
  }

  @Test
  @DisplayName("OSIV 显式关闭，避免长耗时请求把过期实体回写覆盖并发更新")
  void shouldKeepOpenInViewDisabled() {
    // 生成面试报告的接口里有一次两分钟左右的 LLM 调用。OSIV 打开时该请求
    // 全程持有托管实体，收尾的 save() 会把期间被异步消费者改成 COMPLETED 的
    // evaluate_status 又刷回 PROCESSING，界面同时显示「评估中」和得分。
    boolean explicitlyDisabled = loadApplicationYaml().stream()
        .map(source -> source.getProperty("spring.jpa.open-in-view"))
        .anyMatch(value -> "false".equalsIgnoreCase(String.valueOf(value)));
    assertThat(explicitlyDisabled)
        .as("application.yml 必须显式设置 spring.jpa.open-in-view=false")
        .isTrue();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({
      AppConfigProperties.class,
      CorsProperties.class,
      KnowledgeBaseUploadProperties.class,
      LlmPricingProperties.class,
      LlmProviderProperties.class,
      StorageConfigProperties.class
  })
  static class BindingConfiguration {
  }
}
