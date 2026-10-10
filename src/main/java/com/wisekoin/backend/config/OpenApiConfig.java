package com.wisekoin.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("API Documentation for Wisekoin Application")
                                .version("1.0.0")
                                .description("This document describes the system's APIs.")
                                .contact(new Contact().name("APCS 24A01 - DNxDN")));
    }
}
