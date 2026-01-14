package com.buddy.ui.service;

import com.buddy.ui.config.AiConfig;
import com.buddy.ui.model.Message;
import com.buddy.ui.model.dto.ChatMessage;
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

import java.util.ArrayList;
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
    private final ChromaDbService chromaDbService;
    
    @Value("${spring.ai.custom-llm.base-url}")
    private String baseUrl;
    
    @Value("${spring.ai.custom-llm.api-key}")
    private String apiKey;
    
    @Value("${spring.ai.custom-llm.model}")
    private String model;
    
    @Value("${spring.ai.custom-llm.username}")
    private String username;
    
    @Value("${spring.ai.custom-llm.password}")
    private String password;
    
    @Value("${spring.ai.custom-llm.temperature:0.1}")
    private double temperature;
    
    @Value("${spring.ai.custom-llm.max-tokens:300}")
    private int maxTokens;
    
    @Value("${spring.ai.custom-llm.top-p:0.9}")
    private double topP;
    
    @Value("${spring.ai.custom-llm.frequency-penalty:0.5}")
    private double frequencyPenalty;
    
    @Value("${spring.ai.custom-llm.presence-penalty:0.3}")
    private double presencePenalty;
    
    @Value("${spring.ai.custom-llm.seed:-1}")
    private int seed;
    
    private static final int CONTEXT_WINDOW_SIZE = 10; // Last N messages for context
    
    public Message generateResponse(String sessionId, String userMessage) {
        log.debug("Generating AI response for session: {}", sessionId);
        
        // RAG: Search for relevant context from ChromaDB
        String ragContext = retrieveRelevantContext(userMessage);
        
        // Fetch conversation history for context
        Pageable pageable = PageRequest.of(0, CONTEXT_WINDOW_SIZE);
        List<Message> recentMessages = messageRepository.findLastMessagesBySessionId(sessionId, pageable);
        
        // Build messages list with RAG context (system, user, assistant format)
        List<ChatMessage> messages = buildMessagesListWithRAG(recentMessages, userMessage, ragContext);
        
        // Generate AI response using OpenAI-compatible API
        String aiResponse = callCustomLlmApi(messages);
        
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
    
    /**
     * Retrieve relevant context from ChromaDB using RAG
     */
    private String retrieveRelevantContext(String query) {
        try {
            List<Map<String, Object>> results = chromaDbService.searchSimilar(query);
            
            if (results.isEmpty()) {
                return "";
            }
            
            StringBuilder context = new StringBuilder("Bilgi Tabanından İlgili Bilgiler:\n\n");
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> result = results.get(i);
                String text = (String) result.get("text");
                if (text != null && !text.isEmpty()) {
                    context.append(String.format("%d. %s\n", i + 1, text));
                }
            }
            
            return context.toString();
        } catch (Exception e) {
            log.error("Error retrieving RAG context", e);
            return "";
        }
    }
    
    private List<ChatMessage> buildMessagesList(List<Message> recentMessages, String userMessage) {
        return buildMessagesListWithRAG(recentMessages, userMessage, "");
    }
    
    private List<ChatMessage> buildMessagesListWithRAG(List<Message> recentMessages, String userMessage, String ragContext) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // Add system message with persona and RAG context
        String systemMessage = AiConfig.SYSTEM_PROMPT;
        if (!ragContext.isEmpty()) {
            systemMessage += "\n\n### MEVCUT BAĞLAM (RAG)\n" + ragContext;
            systemMessage += "\n\nÖNEMLİ: Yukarıdaki bağlamdan gelen bilgileri kullanarak soruları cevapla. Eğer bağlamda bilgi yoksa, kullanıcıyı ilgili ekibe yönlendir.";
        }
        messages.add(ChatMessage.builder()
                .role("system")
                .content(systemMessage)
                .build());
        
        // Add conversation history (chronological order)
        List<Message> chronological = recentMessages.stream()
                .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
                .collect(Collectors.toList());
        
        for (Message msg : chronological) {
            if (msg.getSenderType() == com.buddy.ui.model.SenderType.USER) {
                messages.add(ChatMessage.builder()
                        .role("user")
                        .content(msg.getContent())
                        .build());
            } else {
                messages.add(ChatMessage.builder()
                        .role("assistant")
                        .content(msg.getContent())
                        .build());
            }
        }
        
        // Add current user message
        messages.add(ChatMessage.builder()
                .role("user")
                .content(userMessage)
                .build());
        
        return messages;
    }
    
    private String callCustomLlmApi(List<ChatMessage> messages) {
        try {
            // OpenAI-compatible endpoint: /chat/completions
            String url = baseUrl.endsWith("/") 
                    ? baseUrl + "chat/completions" 
                    : baseUrl + "/chat/completions";
            
            log.info("Calling API URL: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + apiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("top_p", topP);
            requestBody.put("frequency_penalty", frequencyPenalty);
            requestBody.put("presence_penalty", presencePenalty);
            requestBody.put("seed", seed);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", username);
            metadata.put("pwd", password);
            requestBody.put("metadata", metadata);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.debug("Request URL: {}", url);
            log.debug("Request Body: {}", objectMapper.writeValueAsString(requestBody));
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            
            // Parse response (OpenAI-compatible format)
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode choices = jsonResponse.get("choices");
            
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
            
            throw new RuntimeException("Failed to parse API response");
            
        } catch (Exception e) {
            log.error("Error calling custom LLM API", e);
            throw new RuntimeException("Failed to generate AI response: " + e.getMessage(), e);
        }
    }
    
    
    private Map<String, Object> createMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", model);
        metadata.put("timestamp", System.currentTimeMillis());
        return metadata;
    }
}
