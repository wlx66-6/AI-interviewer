package ai.interviewer.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能 AI 面试官平台 API")
                        .description("简历分析、模拟面试、知识库管理 RESTful API 文档")
                        .version("1.0.0"));
    }
}
