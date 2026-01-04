package com.buddy.ui.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class ChromaDbConfig {
    
    @Value("${spring.chromadb.base-url:http://localhost:8000}")
    private String baseUrl;
    
    @Value("${spring.chromadb.collection-name:buddy-knowledge-base}")
    private String collectionName;
}

