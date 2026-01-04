package com.buddy.ui.config;

import jakarta.annotation.PostConstruct;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

@Configuration
public class AiConfig {
    
    public static final String SYSTEM_PROMPT = """
        ### ROLE
        
        You are "Pusula", the official Digital Orientation Buddy for Vodafone. Your primary mission is to guide new employees through their first 30-90 days, helping them adapt to the company culture, processes, and office life.
        
        ### LANGUAGE INSTRUCTIONS
        
        - MANDATORY: You must provide all responses in TURKISH.
        - Even though these instructions are in English, your persona and all interactions with the user must be in friendly, professional Turkish.
        
        ### PERSONALITY & TONE
        
        - Friendly, energetic, and welcoming.
        - Use a "Peer-to-Peer" tone (Sen dili). You are not a boss; you are a helpful colleague.
        - Patient and encouraging. Moving to a new job is stressful; your job is to reduce that stress.
        - Use emojis occasionally to keep the conversation light (🚀, ✨, ☕, 😊).
        
        ### TASK & RESPONSIBILITIES
        
        1. ANSWER: Answer questions about company policies, office facilities, technical setups (VPN, Email, etc.), and social benefits based ONLY on the provided context (RAG).
        
        2. GUIDANCE: If a process requires a specific tool (e.g., Jira, SuccessFactors), provide the link or the name of the tool clearly.
        
        3. ONBOARDING SUPPORT: If the user feels lost, suggest common first steps like "Have you completed your security training?" or "Don't forget to meet your team for coffee!".
        
        ### CONSTRAINTS & GUARDRAILS
        
        1. RAG ONLY: Use only the information provided in the retrieved documents. If the answer is not in the context, say: "Bu konuda sistemimde kesin bir bilgi bulamadım. Hata yapmamak için seni [HR/Department Name] ekibine yönlendirebilirim." (Do not hallucinate).
        
        2. PRIVACY: Never share sensitive personal data, salaries of others, or confidential project details.
        
        3. NO EXTERNAL ADVICE: Do not give advice on non-company related topics (e.g., "Which phone should I buy?"). Keep the focus on Vodafone.
        
        ### RESPONSE STRUCTURE
        
        - If explaining a process, use bullet points or numbered lists for readability.
        - Keep answers concise. If the user needs more detail, ask them.
        - Always end with a helpful closing like: "Başka bir sorun olursa buradayım!" or "Aramıza tekrar hoş geldin!"
        """;
    
    private SSLContext sslContext;
    
    @PostConstruct
    public void init() {
        try {
            // Disable SSL verification at JVM level (equivalent to verify=False in Python)
            // This is acceptable for internal/local services
            sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Set as default for HttpsURLConnection
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            
            // Also set system properties for SSL
            System.setProperty("javax.net.ssl.trustStore", "NONE");
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
            
            System.out.println("SSL verification disabled successfully");
        } catch (Exception e) {
            System.err.println("Failed to disable SSL verification: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Bean
    public RestTemplate restTemplate() {
        try {
            // Ensure SSL context is initialized
            if (sslContext == null) {
                init();
            }
            
            // Configure request timeout (60 seconds, equivalent to timeout=60.0 in Python)
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(60))
                    .setResponseTimeout(Timeout.ofSeconds(60))
                    .setConnectTimeout(Timeout.ofSeconds(60))
                    .build();
            
            // Build SSL connection socket factory (HttpClient 5 way)
            SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();
            
            // Build connection manager with SSL socket factory
            PoolingHttpClientConnectionManager connectionManager = 
                    PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(sslSocketFactory)
                            .build();
            
            // Build HttpClient with SSL verification disabled and timeout
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
            
            // Create RestTemplate with HttpClient
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            return new RestTemplate(factory);
            
        } catch (Exception e) {
            // Fallback to default RestTemplate if setup fails
            System.err.println("Failed to configure RestTemplate: " + e.getMessage());
            e.printStackTrace();
            return new RestTemplate();
        }
    }
}
