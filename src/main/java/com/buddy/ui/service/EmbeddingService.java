package com.buddy.ui.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Embedding service using OpenAI-compatible API
 * Equivalent to OpenAIEmbeddings in Python
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${spring.ai.custom-llm.base-url}")
    private String baseUrl;
    
    @Value("${spring.ai.custom-llm.api-key}")
    private String apiKey;
    
    @Value("${spring.ai.custom-llm.username}")
    private String username;
    
    @Value("${spring.ai.custom-llm.password}")
    private String password;
    
    @Value("${spring.ai.custom-embedding.model:practicus/gemma-300m-hackathon}")
    private String embeddingModel;
    
    /**
     * Generate embedding for a single text (equivalent to embed_query in Python)
     */
    public List<Float> generateEmbedding(String text) {
        try {
            // OpenAI-compatible embeddings endpoint
            String url = baseUrl.replace("/v1", "") + "/v1/embeddings";
            
            log.debug("Generating embedding for text: {}", text.substring(0, Math.min(50, text.length())));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            Map<String, Object> request = new HashMap<>();
            request.put("model", embeddingModel);
            request.put("input", text);
            
            // Add metadata (equivalent to extra_body in Python)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", username);
            metadata.put("pwd", password);
            request.put("metadata", metadata);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode data = jsonResponse.get("data");
            
            if (data != null && data.isArray() && data.size() > 0) {
                JsonNode embedding = data.get(0).get("embedding");
                if (embedding != null && embedding.isArray()) {
                    List<Float> result = new ArrayList<>();
                    for (JsonNode value : embedding) {
                        result.add((float) value.asDouble());
                    }
                    log.debug("Generated embedding with dimension: {}", result.size());
                    return result;
                }
            }
            
            throw new RuntimeException("Failed to parse embedding response");
            
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate embeddings for multiple texts (equivalent to embed_documents in Python)
     */
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        try {
            // OpenAI-compatible embeddings endpoint
            String url = baseUrl.replace("/v1", "") + "/v1/embeddings";
            
            log.debug("Generating embeddings for {} texts", texts.size());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            Map<String, Object> request = new HashMap<>();
            request.put("model", embeddingModel);
            request.put("input", texts);
            
            // Add metadata (equivalent to extra_body in Python)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", username);
            metadata.put("pwd", password);
            request.put("metadata", metadata);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode data = jsonResponse.get("data");
            
            List<List<Float>> results = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode embedding = item.get("embedding");
                    if (embedding != null && embedding.isArray()) {
                        List<Float> embeddingList = new ArrayList<>();
                        for (JsonNode value : embedding) {
                            embeddingList.add((float) value.asDouble());
                        }
                        results.add(embeddingList);
                    }
                }
            }
            
            log.debug("Generated {} embeddings with dimension: {}", 
                    results.size(), 
                    results.isEmpty() ? 0 : results.get(0).size());
            
            return results;
            
        } catch (Exception e) {
            log.error("Error generating embeddings", e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
        }
    }
}
