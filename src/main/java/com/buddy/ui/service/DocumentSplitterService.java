package com.buddy.ui.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for splitting documents into meaningful chunks
 */
@Service
@Slf4j
public class DocumentSplitterService {
    
    @Value("${spring.rag.chunk-size:500}")
    private int chunkSize;
    
    @Value("${spring.rag.chunk-overlap:50}")
    private int chunkOverlap;
    
    /**
     * Split text into meaningful chunks with overlap
     */
    public List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        
        // Remove extra whitespace and normalize
        text = text.replaceAll("\\s+", " ").trim();
        
        // Split by sentences first (basic approach)
        // This regex splits on sentence endings (. ! ?) followed by whitespace
        String[] sentences = text.split("(?<=[.!?])\\s+");
        
        // If no sentence boundaries found, split by paragraphs
        if (sentences.length == 1) {
            sentences = text.split("\\n\\n+");
        }
        
        // If still no boundaries, split by newlines
        if (sentences.length == 1) {
            sentences = text.split("\\n");
        }
        
        // If still one piece, split by character count
        if (sentences.length == 1 && text.length() > chunkSize) {
            return splitByCharacterCount(text);
        }
        
        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }
            
            int sentenceLength = sentence.length();
            
            // If adding this sentence would exceed chunk size, save current chunk
            if (currentLength + sentenceLength > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                
                // Start new chunk with overlap (last N characters)
                String lastChunk = currentChunk.toString();
                int overlapStart = Math.max(0, lastChunk.length() - chunkOverlap);
                String overlapText = lastChunk.substring(overlapStart);
                
                // Find last sentence in overlap text
                String[] overlapSentences = overlapText.split("(?<=[.!?])\\s+");
                if (overlapSentences.length > 0) {
                    currentChunk = new StringBuilder(overlapSentences[overlapSentences.length - 1]);
                } else {
                    currentChunk = new StringBuilder(overlapText);
                }
                currentLength = currentChunk.length();
            }
            
            if (currentChunk.length() > 0) {
                currentChunk.append(" ");
            }
            currentChunk.append(sentence);
            currentLength += sentenceLength + 1;
        }
        
        // Add remaining chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        log.debug("Split text into {} chunks (chunk size: {}, overlap: {})", 
                chunks.size(), chunkSize, chunkOverlap);
        
        return chunks;
    }
    
    /**
     * Split by character count when no sentence boundaries found
     */
    private List<String> splitByCharacterCount(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end - chunkOverlap; // Overlap
        }
        
        return chunks;
    }
    
    /**
     * Split multiple texts
     */
    public List<String> splitTexts(List<String> texts) {
        List<String> allChunks = new ArrayList<>();
        for (String text : texts) {
            allChunks.addAll(splitText(text));
        }
        return allChunks;
    }
}

