package com.buddy.ui.config;

import com.buddy.ui.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom EmbeddingModel that wraps the existing Embedding API
 */
@RequiredArgsConstructor
@Slf4j
public class CustomEmbeddingModel implements EmbeddingModel {
    
    private final EmbeddingService embeddingService;
    
    @Override
    public Response<Embedding> embed(String text) {
        try {
            List<Float> embeddingFloats = embeddingService.generateEmbedding(text);
            float[] embeddingArray = new float[embeddingFloats.size()];
            for (int i = 0; i < embeddingFloats.size(); i++) {
                embeddingArray[i] = embeddingFloats.get(i);
            }
            Embedding embedding = new Embedding(embeddingArray);
            return new Response<>(embedding);
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        try {
            // Extract text from TextSegments
            List<String> texts = textSegments.stream()
                    .map(TextSegment::text)
                    .collect(Collectors.toList());
            
            // Generate embeddings
            List<List<Float>> embeddingsFloats = embeddingService.generateEmbeddings(texts);
            List<Embedding> embeddings = embeddingsFloats.stream()
                    .map(floats -> {
                        float[] array = new float[floats.size()];
                        for (int i = 0; i < floats.size(); i++) {
                            array[i] = floats.get(i);
                        }
                        return new Embedding(array);
                    })
                    .collect(Collectors.toList());
            return new Response<>(embeddings);
        } catch (Exception e) {
            log.error("Error generating embeddings", e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
        }
    }
}

