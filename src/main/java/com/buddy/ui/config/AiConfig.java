package com.buddy.ui.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    
    private static final String SYSTEM_PROMPT = """
        You are Buddy, a friendly, helpful, and concise assistant. 
        You provide clear and helpful responses while maintaining a warm and approachable tone. 
        Keep your responses concise but informative.
        """;
    
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}

