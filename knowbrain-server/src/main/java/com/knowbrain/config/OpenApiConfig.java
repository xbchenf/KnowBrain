package com.knowbrain.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowBrainOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KnowBrain API")
                        .description("企业私有知识大脑 - 智能知识库 API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("KnowBrain Team")));
    }
}
