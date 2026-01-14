package com.buddy.ui.config;

import com.buddy.ui.service.ChromaDbService;
import com.buddy.ui.service.EmbeddingService;
import com.buddy.ui.tool.AccessRequestTool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j Configuration
 * Integrates RAG (ChromaDB) with Agent capabilities
 * System decides whether to use RAG or Agent based on query intent
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class LangChain4jConfig {
    
    private final ChromaDbService chromaDbService;
    private final EmbeddingService embeddingService;
    private final AccessRequestTool accessRequestTool;
    
    @Value("${spring.ai.custom-llm.base-url}")
    private String baseUrl;
    
    @Value("${spring.ai.custom-llm.api-key}")
    private String apiKey;
    
    @Value("${spring.ai.custom-llm.model}")
    private String model;
    
    @Value("${spring.ai.custom-llm.username}")
    private String username;
    
    @Value("${spring.ai.custom-llm.password}")
    private String password;
    
    @Value("${spring.ai.custom-llm.temperature:0.1}")
    private double temperature;
    
    @Value("${spring.ai.custom-llm.max-tokens:1500}")
    private int maxTokens;
    
    @Value("${spring.ai.custom-llm.top-p:0.9}")
    private double topP;
    
    @Value("${spring.ai.custom-llm.frequency-penalty:0.5}")
    private double frequencyPenalty;
    
    @Value("${spring.ai.custom-llm.presence-penalty:0.3}")
    private double presencePenalty;
    
    @Value("${spring.ai.custom-llm.seed:-1}")
    private int seed;
    
    @Value("${spring.chromadb.top-k:5}")
    private int topK;
    
    /**
     * Custom ChatLanguageModel that uses existing LLM API
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return new CustomChatLanguageModel(
            baseUrl, apiKey, model, username, password,
            temperature, maxTokens, topP, frequencyPenalty, presencePenalty, seed
        );
    }
    
    /**
     * Custom EmbeddingModel that uses existing Embedding API
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new CustomEmbeddingModel(embeddingService);
    }
    
    /**
     * EmbeddingStore that wraps ChromaDB
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new ChromaDbEmbeddingStore(chromaDbService, embeddingService);
    }
    
    /**
     * ContentRetriever for RAG
     * Retrieves relevant documents from ChromaDB
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(topK)
                .minScore(0.6) // Minimum similarity score
                .build();
    }
    
    /**
     * MessageWindowChatMemory for conversation history
     * Keeps last 10 messages
     */
    @Bean
    public MessageWindowChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(10);
    }
}

