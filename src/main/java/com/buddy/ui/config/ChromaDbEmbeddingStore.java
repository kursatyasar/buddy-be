package com.buddy.ui.config;

import com.buddy.ui.service.ChromaDbService;
import com.buddy.ui.service.EmbeddingService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * EmbeddingStore implementation that wraps ChromaDB
 * This allows LangChain4j to use existing ChromaDB infrastructure
 */
@RequiredArgsConstructor
@Slf4j
public class ChromaDbEmbeddingStore implements EmbeddingStore<TextSegment> {
    
    private final ChromaDbService chromaDbService;
    private final EmbeddingService embeddingService;
    
    @Override
    public String add(Embedding embedding) {
        // This method is typically used for adding single embeddings
        // For ChromaDB, we use addAll for batch operations
        log.warn("Single add operation not fully implemented for ChromaDB");
        return "not-implemented";
    }
    
    @Override
    public void add(String id, Embedding embedding) {
        // Single add with ID - not typically used with ChromaDB
        log.warn("Single add with ID operation not fully implemented for ChromaDB");
    }
    
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        // Add embedding with text segment
        // This would require converting to ChromaDB format
        log.warn("Single add with text segment operation not fully implemented for ChromaDB");
        return "not-implemented";
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        // Batch add embeddings
        // This would require text segments which we don't have here
        log.warn("Batch add embeddings without text segments not fully implemented for ChromaDB");
        return List.of();
    }
    
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        try {
            // Convert TextSegments to strings
            List<String> texts = textSegments.stream()
                    .map(TextSegment::text)
                    .collect(Collectors.toList());
            
            // Generate IDs - TextSegment doesn't have id() method, so we generate UUIDs
            // In a real implementation, you might want to store IDs in metadata when creating segments
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < textSegments.size(); i++) {
                ids.add(java.util.UUID.randomUUID().toString());
            }
            
            // Add to ChromaDB
            Map<String, Object> metadata = new java.util.HashMap<>();
            chromaDbService.addDocuments(texts, ids, metadata);
            
            log.info("Added {} documents to ChromaDB", texts.size());
            return ids;
            
        } catch (Exception e) {
            log.error("Error adding documents to ChromaDB", e);
            throw new RuntimeException("Failed to add documents to ChromaDB: " + e.getMessage(), e);
        }
    }
    
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        try {
            // Get query embedding from request
            Embedding queryEmbedding = request.queryEmbedding();
            
            // Convert Embedding to List<Float> for ChromaDB
            float[] embeddingArray = queryEmbedding.vector();
            List<Float> embeddingList = new ArrayList<>();
            for (float f : embeddingArray) {
                embeddingList.add(f);
            }
            
            // Search in ChromaDB using embedding directly
            int maxResults = request.maxResults();
            if (maxResults <= 0) {
                maxResults = 5; // Default value
            }
            
            // Use ChromaDB's embedding-based search
            List<Map<String, Object>> results = chromaDbService.searchSimilarByEmbedding(embeddingList, maxResults);
            
            // Convert results to EmbeddingMatch format
            List<EmbeddingMatch<TextSegment>> matches = results.stream()
                    .map(result -> {
                        String text = (String) result.get("text");
                        String id = (String) result.getOrDefault("id", java.util.UUID.randomUUID().toString());
                        Double distance = result.get("distance") != null ? 
                                ((Number) result.get("distance")).doubleValue() : 0.0;
                        
                        // Create TextSegment with Metadata
                        Metadata metadata = new Metadata();
                        metadata.add("id", id);
                        TextSegment segment = new TextSegment(text, metadata);
                        
                        // Generate embedding for the segment
                        // Note: In a real implementation, you'd store the embedding with the segment
                        List<Float> embeddingFloats = embeddingService.generateEmbedding(text);
                        float[] segmentEmbeddingArray = new float[embeddingFloats.size()];
                        for (int i = 0; i < embeddingFloats.size(); i++) {
                            segmentEmbeddingArray[i] = embeddingFloats.get(i);
                        }
                        Embedding embedding = new Embedding(segmentEmbeddingArray);
                        
                        // Convert distance to score (ChromaDB uses distance, LangChain4j uses score)
                        // Lower distance = higher score
                        // Normalize score to 0-1 range
                        double score = 1.0 / (1.0 + distance);
                        
                        return new EmbeddingMatch<>(score, id, embedding, segment);
                    })
                    .collect(Collectors.toList());
            
            return new EmbeddingSearchResult<>(matches);
            
        } catch (Exception e) {
            log.error("Error searching ChromaDB", e);
            return new EmbeddingSearchResult<>(List.of());
        }
    }
    
    // Note: remove() and removeAll() methods may not be in EmbeddingStore interface
    // If they are optional or not present, these methods can be removed
    // For now, keeping them without @Override annotation
    public void remove(String id) {
        // ChromaDB deletion can be implemented if needed
        log.warn("Document deletion not fully implemented for ChromaDB");
    }
    
    public void removeAll(List<String> ids) {
        // ChromaDB batch deletion can be implemented if needed
        log.warn("Batch document deletion not fully implemented for ChromaDB");
    }
}

