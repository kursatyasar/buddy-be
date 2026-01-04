package com.buddy.ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for training RAG system with plain text
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextTrainingService {
    
    private final DocumentSplitterService documentSplitterService;
    private final EmbeddingService embeddingService;
    private final ChromaDbService chromaDbService;
    
    /**
     * Process and train with plain text
     */
    public Map<String, Object> trainWithText(String text, Map<String, Object> metadata) {
        try {
            log.info("Processing text (length: {} characters)", text != null ? text.length() : 0);
            
            if (text == null || text.trim().isEmpty()) {
                throw new RuntimeException("Text cannot be empty");
            }
            
            // Split into chunks
            List<String> chunks = documentSplitterService.splitText(text);
            log.info("Split text into {} chunks", chunks.size());
            
            // Generate IDs for chunks
            String baseId = "text-" + System.currentTimeMillis();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ids.add(baseId + "-chunk-" + i);
            }
            
            // Prepare metadata for each chunk
            Map<String, Object> chunkMetadata = new HashMap<>();
            if (metadata != null) {
                chunkMetadata.putAll(metadata);
            }
            chunkMetadata.put("type", "text");
            chunkMetadata.put("total_chunks", chunks.size());
            
            // Add to ChromaDB
            chromaDbService.addDocuments(chunks, ids, chunkMetadata);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Text processed and added to knowledge base");
            result.put("chunks", chunks.size());
            result.put("totalCharacters", text.length());
            
            log.info("Successfully processed text ({} chunks)", chunks.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing text", e);
            throw new RuntimeException("Failed to process text: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process and train with multiple texts
     */
    public Map<String, Object> trainWithTexts(List<String> texts, Map<String, Object> metadata) {
        try {
            log.info("Processing {} texts", texts.size());
            
            if (texts == null || texts.isEmpty()) {
                throw new RuntimeException("Texts cannot be empty");
            }
            
            // Split all texts into chunks
            List<String> allChunks = documentSplitterService.splitTexts(texts);
            log.info("Split {} texts into {} total chunks", texts.size(), allChunks.size());
            
            // Generate IDs for chunks
            String baseId = "texts-" + System.currentTimeMillis();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < allChunks.size(); i++) {
                ids.add(baseId + "-chunk-" + i);
            }
            
            // Prepare metadata
            Map<String, Object> chunkMetadata = new HashMap<>();
            if (metadata != null) {
                chunkMetadata.putAll(metadata);
            }
            chunkMetadata.put("type", "texts");
            chunkMetadata.put("total_texts", texts.size());
            chunkMetadata.put("total_chunks", allChunks.size());
            
            // Add to ChromaDB
            chromaDbService.addDocuments(allChunks, ids, chunkMetadata);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Texts processed and added to knowledge base");
            result.put("textCount", texts.size());
            result.put("chunks", allChunks.size());
            
            log.info("Successfully processed {} texts ({} chunks)", texts.size(), allChunks.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing texts", e);
            throw new RuntimeException("Failed to process texts: " + e.getMessage(), e);
        }
    }
}

