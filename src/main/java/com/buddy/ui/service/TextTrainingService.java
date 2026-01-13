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
    
    /**
     * Process and train with documents (each document has its own text and metadata)
     * Input format: List of {text: "...", metadata: {...}}
     * Each document will be split into chunks, and each chunk will inherit the document's metadata
     */
    public Map<String, Object> trainWithDocuments(List<Map<String, Object>> documents) {
        try {
            log.info("Processing {} documents with individual metadata", documents.size());
            
            if (documents == null || documents.isEmpty()) {
                throw new RuntimeException("Documents cannot be empty");
            }
            
            List<String> allTexts = new ArrayList<>();
            List<Map<String, Object>> allMetadatas = new ArrayList<>();
            int documentIndex = 0;
            int totalDocumentsProcessed = 0;
            
            for (Map<String, Object> doc : documents) {
                String text = (String) doc.get("text");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
                
                if (text == null || text.trim().isEmpty()) {
                    log.warn("Skipping document {} due to empty text", documentIndex);
                    documentIndex++;
                    continue;
                }
                
                // Split text into chunks
                List<String> chunks = documentSplitterService.splitText(text);
                log.debug("Document {} split into {} chunks", documentIndex, chunks.size());
                
                // For each chunk, use the document's metadata
                for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                    allTexts.add(chunks.get(chunkIndex));
                    
                    // Create metadata for this chunk - copy original metadata and add chunk info
                    Map<String, Object> chunkMetadata = new HashMap<>();
                    if (metadata != null) {
                        chunkMetadata.putAll(metadata);
                    }
                    chunkMetadata.put("type", "document");
                    chunkMetadata.put("document_index", documentIndex);
                    chunkMetadata.put("chunk_index", chunkIndex);
                    chunkMetadata.put("total_chunks_in_document", chunks.size());
                    allMetadatas.add(chunkMetadata);
                }
                
                totalDocumentsProcessed++;
                documentIndex++;
            }
            
            if (allTexts.isEmpty()) {
                throw new RuntimeException("No valid texts found in documents");
            }
            
            // Generate IDs
            String baseId = "docs-" + System.currentTimeMillis();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < allTexts.size(); i++) {
                ids.add(baseId + "-chunk-" + i);
            }
            
            // Add to ChromaDB with individual metadata for each chunk
            chromaDbService.addDocumentsWithIndividualMetadata(allTexts, ids, allMetadatas);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Documents processed and added to knowledge base");
            result.put("documentCount", totalDocumentsProcessed);
            result.put("totalDocumentsReceived", documents.size());
            result.put("chunks", allTexts.size());
            
            log.info("Successfully processed {} documents ({} total chunks)", totalDocumentsProcessed, allTexts.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing documents", e);
            throw new RuntimeException("Failed to process documents: " + e.getMessage(), e);
        }
    }
}

