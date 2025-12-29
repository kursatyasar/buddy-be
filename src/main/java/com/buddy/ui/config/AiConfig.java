package com.buddy.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiConfig {
    
    public static final String SYSTEM_PROMPT = """
        You are Buddy, a friendly, helpful, and concise assistant. 
        You provide clear and helpful responses while maintaining a warm and approachable tone. 
        Keep your responses concise but informative.
        """;
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
