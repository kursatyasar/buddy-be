package com.buddy.ui.service;

import com.buddy.ui.config.ChromaDbConfig;
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
 * Service for interacting with ChromaDB vector database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChromaDbService {
    
    private final RestTemplate restTemplate;
    private final ChromaDbConfig chromaDbConfig;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${spring.chromadb.top-k:5}")
    private int topK;
    
    /**
     * Initialize or get collection
     */
    public void ensureCollection() {
        try {
            String url = chromaDbConfig.getBaseUrl() + "/api/v1/collections";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Check if collection exists
            Map<String, Object> getRequest = new HashMap<>();
            getRequest.put("name", chromaDbConfig.getCollectionName());
            
            HttpEntity<Map<String, Object>> getEntity = new HttpEntity<>(getRequest, headers);
            ResponseEntity<String> getResponse = restTemplate.exchange(
                    url, HttpMethod.GET, getEntity, String.class);
            
            JsonNode collections = objectMapper.readTree(getResponse.getBody());
            boolean exists = false;
            
            if (collections.isArray()) {
                for (JsonNode collection : collections) {
                    if (collection.has("name") && 
                        collection.get("name").asText().equals(chromaDbConfig.getCollectionName())) {
                        exists = true;
                        break;
                    }
                }
            }
            
            if (!exists) {
                // Create collection
                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", chromaDbConfig.getCollectionName());
                createRequest.put("metadata", new HashMap<>());
                
                HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createRequest, headers);
                restTemplate.exchange(url, HttpMethod.POST, createEntity, String.class);
                log.info("Created ChromaDB collection: {}", chromaDbConfig.getCollectionName());
            } else {
                log.info("ChromaDB collection already exists: {}", chromaDbConfig.getCollectionName());
            }
            
        } catch (Exception e) {
            log.error("Error ensuring ChromaDB collection", e);
        }
    }
    
    /**
     * Add documents to ChromaDB
     */
    public void addDocuments(List<String> texts, List<String> ids, Map<String, Object> metadata) {
        try {
            ensureCollection();
            
            String url = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + 
                        chromaDbConfig.getCollectionName() + "/add";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Generate embeddings
            List<List<Float>> embeddings = embeddingService.generateEmbeddings(texts);
            
            Map<String, Object> request = new HashMap<>();
            request.put("ids", ids);
            request.put("embeddings", embeddings);
            request.put("documents", texts);
            if (metadata != null) {
                // ChromaDB expects metadatas as a list, one metadata object per document
                List<Map<String, Object>> metadatas = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    metadatas.add(metadata);
                }
                request.put("metadatas", metadatas);
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            log.debug("Added {} documents to ChromaDB", texts.size());
            
        } catch (Exception e) {
            log.error("Error adding documents to ChromaDB", e);
            throw new RuntimeException("Failed to add documents to ChromaDB: " + e.getMessage(), e);
        }
    }
    
    /**
     * Search for similar documents
     */
    public List<Map<String, Object>> searchSimilar(String queryText, int nResults) {
        try {
            ensureCollection();
            
            String url = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + 
                        chromaDbConfig.getCollectionName() + "/query";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Generate query embedding
            List<Float> queryEmbedding = embeddingService.generateEmbedding(queryText);
            
            Map<String, Object> request = new HashMap<>();
            request.put("query_embeddings", Collections.singletonList(queryEmbedding));
            request.put("n_results", nResults);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            List<Map<String, Object>> results = new ArrayList<>();
            
            if (jsonResponse.has("documents") && jsonResponse.get("documents").isArray()) {
                JsonNode documents = jsonResponse.get("documents").get(0); // First query result
                JsonNode distances = jsonResponse.has("distances") ? 
                        jsonResponse.get("distances").get(0) : null;
                JsonNode ids = jsonResponse.has("ids") ? 
                        jsonResponse.get("ids").get(0) : null;
                
                if (documents.isArray()) {
                    for (int i = 0; i < documents.size(); i++) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("text", documents.get(i).asText());
                        if (distances != null && distances.isArray() && i < distances.size()) {
                            result.put("distance", distances.get(i).asDouble());
                        }
                        if (ids != null && ids.isArray() && i < ids.size()) {
                            result.put("id", ids.get(i).asText());
                        }
                        results.add(result);
                    }
                }
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Error searching ChromaDB", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Search for similar documents using default top-k
     */
    public List<Map<String, Object>> searchSimilar(String queryText) {
        return searchSimilar(queryText, topK);
    }
}

