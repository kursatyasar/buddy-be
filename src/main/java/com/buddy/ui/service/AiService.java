package com.buddy.ui.service;

import com.buddy.ui.config.AiConfig;
import com.buddy.ui.model.Message;
import com.buddy.ui.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {
    
    private final MessageRepository messageRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${spring.ai.gemini.api-key}")
    private String apiKey;
    
    @Value("${spring.ai.gemini.chat.options.model:gemini-1.5-flash-001}")
    private String model;
    
    private static final int CONTEXT_WINDOW_SIZE = 10; // Last N messages for context
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    
    public Message generateResponse(String sessionId, String userMessage) {
        log.debug("Generating AI response for session: {}", sessionId);
        
        // Fetch conversation history for context
        Pageable pageable = PageRequest.of(0, CONTEXT_WINDOW_SIZE);
        List<Message> recentMessages = messageRepository.findLastMessagesBySessionId(sessionId, pageable);
        
        // Build conversation context
        String conversationContext = buildConversationContext(recentMessages);
        
        // Build full prompt with system message
        String fullPrompt = AiConfig.SYSTEM_PROMPT + "\n\n" + conversationContext + 
                           "\n\nUser: " + userMessage + "\n\nBuddy:";
        
        // Generate AI response using Gemini API
        String aiResponse = callGeminiApi(fullPrompt);
        
        log.debug("AI response generated successfully");
        
        // Create AI message entity
        Message aiMessage = Message.builder()
                .sessionId(sessionId)
                .senderType(com.buddy.ui.model.SenderType.AI)
                .content(aiResponse)
                .metadata(createMetadata())
                .build();
        
        return aiMessage;
    }
    
    private String callGeminiApi(String prompt) {
        try {
            String url = String.format(GEMINI_API_URL, model, apiKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            
            content.put("parts", List.of(part));
            requestBody.put("contents", List.of(content));
            
            // Add generation config
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topP", 0.95);
            generationConfig.put("topK", 40);
            generationConfig.put("maxOutputTokens", 1000);
            requestBody.put("generationConfig", generationConfig);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            
            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode candidates = jsonResponse.get("candidates");
            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode candidateContent = candidates.get(0).get("content");
                if (candidateContent != null) {
                    JsonNode parts = candidateContent.get("parts");
                    if (parts != null && parts.isArray() && parts.size() > 0) {
                        JsonNode text = parts.get(0).get("text");
                        if (text != null) {
                            return text.asText();
                        }
                    }
                }
            }
            
            throw new RuntimeException("Failed to parse Gemini API response");
            
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to generate AI response: " + e.getMessage(), e);
        }
    }
    
    private String buildConversationContext(List<Message> messages) {
        if (messages.isEmpty()) {
            return "This is the start of a new conversation.";
        }
        
        // Reverse to get chronological order
        List<Message> chronological = messages.stream()
                .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
                .collect(Collectors.toList());
        
        StringBuilder context = new StringBuilder("Previous conversation:\n");
        for (Message msg : chronological) {
            String sender = msg.getSenderType() == com.buddy.ui.model.SenderType.USER ? "User" : "Buddy";
            context.append(sender).append(": ").append(msg.getContent()).append("\n");
        }
        
        return context.toString();
    }
    
    private Map<String, Object> createMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", model);
        metadata.put("timestamp", System.currentTimeMillis());
        return metadata;
    }
}
