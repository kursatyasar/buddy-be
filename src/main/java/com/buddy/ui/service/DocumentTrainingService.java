package com.buddy.ui.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Service for training RAG system with PDF/Word documents
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentTrainingService {
    
    private final DocumentSplitterService documentSplitterService;
    private final EmbeddingService embeddingService;
    private final ChromaDbService chromaDbService;
    private final Tika tika = new Tika();
    
    /**
     * Process and train with a document (PDF/Word)
     */
    public Map<String, Object> trainWithDocument(MultipartFile file, Map<String, Object> metadata) {
        try {
            log.info("Processing document: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());
            
            // Extract text from document
            String extractedText = extractTextFromDocument(file);
            
            if (extractedText == null || extractedText.trim().isEmpty()) {
                throw new RuntimeException("No text could be extracted from the document");
            }
            
            log.debug("Extracted {} characters from document", extractedText.length());
            
            // Split into chunks
            List<String> chunks = documentSplitterService.splitText(extractedText);
            log.info("Split document into {} chunks", chunks.size());
            
            // Generate IDs for chunks
            String baseId = generateBaseId(file.getOriginalFilename());
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ids.add(baseId + "-chunk-" + i);
            }
            
            // Prepare metadata for each chunk
            Map<String, Object> chunkMetadata = new HashMap<>();
            if (metadata != null) {
                chunkMetadata.putAll(metadata);
            }
            chunkMetadata.put("source", file.getOriginalFilename());
            chunkMetadata.put("type", getFileType(file.getOriginalFilename()));
            chunkMetadata.put("total_chunks", chunks.size());
            
            // Add to ChromaDB
            chromaDbService.addDocuments(chunks, ids, chunkMetadata);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Document processed and added to knowledge base");
            result.put("filename", file.getOriginalFilename());
            result.put("chunks", chunks.size());
            result.put("totalCharacters", extractedText.length());
            
            log.info("Successfully processed document: {} ({} chunks)", file.getOriginalFilename(), chunks.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing document: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract text from document using Apache Tika
     */
    private String extractTextFromDocument(MultipartFile file) throws IOException, TikaException {
        try {
            String detectedType = tika.detect(file.getInputStream());
            log.debug("Detected document type: {}", detectedType);
            
            String text = tika.parseToString(file.getInputStream());
            return text;
        } catch (Exception e) {
            log.error("Error extracting text from document", e);
            throw new IOException("Failed to extract text from document: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate base ID from filename
     */
    private String generateBaseId(String filename) {
        if (filename == null) {
            return "doc-" + System.currentTimeMillis();
        }
        String baseName = filename.replaceAll("[^a-zA-Z0-9]", "-");
        return baseName.toLowerCase() + "-" + System.currentTimeMillis();
    }
    
    /**
     * Get file type from filename
     */
    private String getFileType(String filename) {
        if (filename == null) {
            return "unknown";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "pdf";
        } else if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "word";
        } else if (lower.endsWith(".txt")) {
            return "text";
        }
        return "unknown";
    }
}

