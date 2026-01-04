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
        You are Buddy, a friendly, helpful, and concise assistant. 
        You provide clear and helpful responses while maintaining a warm and approachable tone. 
        Keep your responses concise but informative.
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
