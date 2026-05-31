package com.wex.anomaly.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Anomaly Detector API")
                        .description("""
                                AI-powered payment anomaly detection using OpenAI function calling.
                                
                                ## How it works
                                1. Submit a transaction via POST /api/transactions/analyse
                                2. OpenAI analyses using function calling
                                3. Model calls one of: mark_normal, mark_suspicious, mark_needs_review
                                4. Result routed to appropriate Kafka topic
                                5. Full audit trail stored in database
                                
                                ## Status values
                                - NORMAL → normal-transactions topic
                                - SUSPICIOUS → suspicious-transactions topic
                                - NEEDS_REVIEW → review-transactions topic
                                - FALLBACK → OpenAI failed, routed to review-transactions
                                
                                ## Idempotency
                                Duplicate transactionId returns cached result without calling OpenAI again.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Sukanya Sinha")
                                .email("sukanyasinha7776@gmail.com")
                                .url("https://linkedin.com/in/sukanyasinha0304"))
                        .license(new License()
                                .name("Personal Project")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server")))
                .tags(List.of(
                        new Tag()
                                .name("Transaction Analysis")
                                .description("AI-powered payment transaction analysis"),
                        new Tag()
                                .name("Health")
                                .description("Health check endpoints")));
    }
}