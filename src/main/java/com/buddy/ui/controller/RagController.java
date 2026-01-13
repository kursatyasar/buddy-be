package com.buddy.ui.controller;

import com.buddy.ui.service.ChromaDbService;
import com.buddy.ui.service.DocumentTrainingService;
import com.buddy.ui.service.TextTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for RAG operations (training and searching)
 */
@RestController
@RequestMapping("/api/v1/rag")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@RequiredArgsConstructor
@Slf4j
public class RagController {
    
    private final ChromaDbService chromaDbService;
    private final DocumentTrainingService documentTrainingService;
    private final TextTrainingService textTrainingService;
    
    /**
     * Train with document (PDF/Word)
     */
    @PostMapping("/train/document")
    public ResponseEntity<Map<String, Object>> trainWithDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "category", required = false) String category) {
        try {
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "File cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Prepare metadata
            Map<String, Object> metadata = new HashMap<>();
            if (source != null) {
                metadata.put("source", source);
            }
            if (category != null) {
                metadata.put("category", category);
            }
            
            Map<String, Object> result = documentTrainingService.trainWithDocument(file, metadata);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error training with document", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to train with document: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Train with plain text
     */
    @PostMapping("/train/text")
    public ResponseEntity<Map<String, Object>> trainWithText(@RequestBody Map<String, Object> request) {
        try {
            String text = (String) request.get("text");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
            
            if (text == null || text.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Text cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }
            
            Map<String, Object> result = textTrainingService.trainWithText(text, metadata);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error training with text", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to train with text: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Train with multiple texts
     */
    @PostMapping("/train/texts")
    public ResponseEntity<Map<String, Object>> trainWithTexts(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> texts = (List<String>) request.get("texts");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
            
            if (texts == null || texts.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Texts cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }
            
            Map<String, Object> result = textTrainingService.trainWithTexts(texts, metadata);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error training with texts", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to train with texts: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Add documents to the knowledge base (legacy endpoint - kept for backward compatibility)
     */
    @PostMapping("/documents")
    public ResponseEntity<Map<String, Object>> addDocuments(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> texts = (List<String>) request.get("texts");
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) request.get("ids");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
            
            if (texts == null || texts.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Texts cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Generate IDs if not provided
            if (ids == null || ids.size() != texts.size()) {
                ids = new java.util.ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    ids.add("doc-" + System.currentTimeMillis() + "-" + i);
                }
            }
            
            chromaDbService.addDocuments(texts, ids, metadata);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Documents added successfully");
            response.put("count", texts.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error adding documents", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to add documents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Train with documents (each document has its own text and metadata)
     * Request body format: [{"text": "...", "metadata": {...}}, ...]
     * Each document will be split into chunks, and each chunk will inherit the document's metadata
     */
    @PostMapping("/train/documents")
    public ResponseEntity<Map<String, Object>> trainWithDocuments(@RequestBody List<Map<String, Object>> documents) {
        try {
            if (documents == null || documents.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Documents cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }
            
            Map<String, Object> result = textTrainingService.trainWithDocuments(documents);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error training with documents", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to train with documents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Search for similar documents
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query, 
                                                       @RequestParam(defaultValue = "5") int topK) {
        try {
            List<Map<String, Object>> results = chromaDbService.searchSimilar(query, topK);
            
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("results", results);
            response.put("count", results.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error searching documents", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to search documents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

