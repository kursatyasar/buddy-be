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
    
    private String collectionId = null;
    
    /**
     * Initialize or get collection and return its ID
     * Using ChromaDB v1 API (more stable and widely supported)
     */
    public String ensureCollection() {
        try {
            if (collectionId != null) {
                return collectionId;
            }
            
            String baseUrl = chromaDbConfig.getBaseUrl();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String collectionName = chromaDbConfig.getCollectionName();
            
            // Try to get collection by name using v1 API
            String getUrl = baseUrl + "/api/v1/collections/" + collectionName;
            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            
            try {
                ResponseEntity<String> getResponse = restTemplate.exchange(
                        getUrl, HttpMethod.GET, getEntity, String.class);
                
                if (getResponse.getStatusCode().is2xxSuccessful()) {
                    JsonNode collection = objectMapper.readTree(getResponse.getBody());
                    if (collection.has("id")) {
                        collectionId = collection.get("id").asText();
                        log.info("Found existing ChromaDB collection: {} (ID: {})", 
                                collectionName, collectionId);
                        return collectionId;
                    } else if (collection.has("collection")) {
                        JsonNode coll = collection.get("collection");
                        if (coll.has("id")) {
                            collectionId = coll.get("id").asText();
                            log.info("Found existing ChromaDB collection: {} (ID: {})", 
                                    collectionName, collectionId);
                            return collectionId;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Collection not found by name, will try to create: {}", e.getMessage());
            }
            
            // Collection doesn't exist, try to create it using v1 API
            String createUrl = baseUrl + "/api/v1/collections";
            Map<String, Object> createRequest = new HashMap<>();
            createRequest.put("name", collectionName);
            // Don't include metadata if empty - ChromaDB 0.4.24 requires non-empty metadata or no metadata at all
            // createRequest.put("metadata", new HashMap<>()); // Removed - causes error in ChromaDB 0.4.24
            
            HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createRequest, headers);
            
            try {
                // Try POST first
                ResponseEntity<String> createResponse = restTemplate.exchange(
                        createUrl, HttpMethod.POST, createEntity, String.class);
                
                if (createResponse.getStatusCode().is2xxSuccessful()) {
                    JsonNode createdCollection = objectMapper.readTree(createResponse.getBody());
                    if (createdCollection.has("id")) {
                        collectionId = createdCollection.get("id").asText();
                    } else if (createdCollection.has("collection")) {
                        JsonNode collection = createdCollection.get("collection");
                        if (collection.has("id")) {
                            collectionId = collection.get("id").asText();
                        }
                    }
                    
                    if (collectionId != null) {
                        log.info("Created ChromaDB collection: {} (ID: {})", 
                                collectionName, collectionId);
                        return collectionId;
                    } else {
                        // If ID not in response, try to get it by name
                        log.debug("Collection created but ID not in response, trying to get by name");
                        ResponseEntity<String> getAfterCreate = restTemplate.exchange(
                                getUrl, HttpMethod.GET, getEntity, String.class);
                        if (getAfterCreate.getStatusCode().is2xxSuccessful()) {
                            JsonNode coll = objectMapper.readTree(getAfterCreate.getBody());
                            if (coll.has("id")) {
                                collectionId = coll.get("id").asText();
                                return collectionId;
                            }
                        }
                        throw new RuntimeException("Collection created but ID not found in response");
                    }
                } else {
                    throw new RuntimeException("Failed to create collection: " + createResponse.getStatusCode());
                }
            } catch (Exception e) {
                // If creation fails, try to get it again (might have been created by another process)
                log.warn("Failed to create collection, trying to get it again: {}", e.getMessage());
                
                try {
                    ResponseEntity<String> retryResponse = restTemplate.exchange(
                            getUrl, HttpMethod.GET, getEntity, String.class);
                    
                    if (retryResponse.getStatusCode().is2xxSuccessful()) {
                        JsonNode retryCollection = objectMapper.readTree(retryResponse.getBody());
                        if (retryCollection.has("id")) {
                            collectionId = retryCollection.get("id").asText();
                            log.info("Found ChromaDB collection after retry: {} (ID: {})", 
                                    collectionName, collectionId);
                            return collectionId;
                        }
                    }
                } catch (Exception e2) {
                    log.error("Failed to get collection on retry", e2);
                }
                
                throw new RuntimeException("Failed to create or find ChromaDB collection. " +
                        "Please ensure ChromaDB is running and the collection name is correct. " +
                        "Error: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            log.error("Error ensuring ChromaDB collection", e);
            throw new RuntimeException("Failed to ensure ChromaDB collection: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add documents to ChromaDB
     */
    public void addDocuments(List<String> texts, List<String> ids, Map<String, Object> metadata) {
        try {
            String collId = ensureCollection();
            
            // Try /upsert endpoint first (newer ChromaDB versions, using v1 API)
            String url = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + collId + "/upsert";
            
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
            
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                log.debug("Added {} documents to ChromaDB collection {} using upsert", texts.size(), collId);
            } catch (Exception e) {
                // If upsert fails, try /add endpoint (older ChromaDB versions, using v1 API)
                log.debug("Upsert failed, trying /add endpoint: {}", e.getMessage());
                String addUrl = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + collId + "/add";
                ResponseEntity<String> response = restTemplate.exchange(addUrl, HttpMethod.POST, entity, String.class);
                log.debug("Added {} documents to ChromaDB collection {} using add", texts.size(), collId);
            }
            
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
            String collId = ensureCollection();
            
            // Use collection ID instead of name (using v1 API)
            String url = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + collId + "/query";
            
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
    
    /**
     * Search for similar documents using embedding vector directly
     * This is used by LangChain4j EmbeddingStore
     */
    public List<Map<String, Object>> searchSimilarByEmbedding(List<Float> queryEmbedding, int nResults) {
        try {
            String collId = ensureCollection();
            
            // Use collection ID instead of name (using v1 API)
            String url = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + collId + "/query";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
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
            log.error("Error searching ChromaDB by embedding", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Add documents to ChromaDB with individual metadata for each document
     * @param texts List of text documents
     * @param ids List of document IDs (must match texts size)
     * @param metadatas List of metadata objects (one per document, must match texts size)
     */
    public void addDocumentsWithIndividualMetadata(List<String> texts, List<String> ids, List<Map<String, Object>> metadatas) {
        try {
            if (texts.size() != ids.size() || texts.size() != metadatas.size()) {
                throw new IllegalArgumentException("Texts, IDs, and metadatas lists must have the same size");
            }
            
            String collId = ensureCollection();
            
            // Try /upsert endpoint first (newer ChromaDB versions, using v1 API)
            String url = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + collId + "/upsert";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Generate embeddings
            List<List<Float>> embeddings = embeddingService.generateEmbeddings(texts);
            
            Map<String, Object> request = new HashMap<>();
            request.put("ids", ids);
            request.put("embeddings", embeddings);
            request.put("documents", texts);
            request.put("metadatas", metadatas); // Each document has its own metadata
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                log.debug("Added {} documents to ChromaDB collection {} with individual metadata using upsert", texts.size(), collId);
            } catch (Exception e) {
                // If upsert fails, try /add endpoint (older ChromaDB versions, using v1 API)
                log.debug("Upsert failed, trying /add endpoint: {}", e.getMessage());
                String addUrl = chromaDbConfig.getBaseUrl() + "/api/v1/collections/" + collId + "/add";
                ResponseEntity<String> response = restTemplate.exchange(addUrl, HttpMethod.POST, entity, String.class);
                log.debug("Added {} documents to ChromaDB collection {} with individual metadata using add", texts.size(), collId);
            }
            
        } catch (Exception e) {
            log.error("Error adding documents to ChromaDB with individual metadata", e);
            throw new RuntimeException("Failed to add documents to ChromaDB: " + e.getMessage(), e);
        }
    }
}

