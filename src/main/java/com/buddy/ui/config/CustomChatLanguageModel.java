package com.buddy.ui.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom ChatLanguageModel that wraps the existing OpenAI-compatible LLM API
 * Supports tool calling for LangChain4j agents
 */
@Slf4j
public class CustomChatLanguageModel implements ChatLanguageModel {
    
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String username;
    private final String password;
    private final double temperature;
    private final int maxTokens;
    private final double topP;
    private final double frequencyPenalty;
    private final double presencePenalty;
    private final int seed;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public CustomChatLanguageModel(
            String baseUrl, String apiKey, String model, String username, String password,
            double temperature, int maxTokens, double topP, 
            double frequencyPenalty, double presencePenalty, int seed) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.username = username;
        this.password = password;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.frequencyPenalty = frequencyPenalty;
        this.presencePenalty = presencePenalty;
        this.seed = seed;
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generateInternal(messages, null);
    }
    
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generateInternal(messages, toolSpecifications);
    }
    
    private Response<AiMessage> generateInternal(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        try {
            // Convert LangChain4j messages to OpenAI format
            List<Map<String, Object>> openAiMessages = new ArrayList<>();
            
            for (ChatMessage message : messages) {
                Map<String, Object> msg = new HashMap<>();
                
                if (message instanceof UserMessage) {
                    msg.put("role", "user");
                    msg.put("content", ((UserMessage) message).singleText());
                } else if (message instanceof AiMessage) {
                    AiMessage aiMsg = (AiMessage) message;
                    msg.put("role", "assistant");
                    
                    // Check if AI message has tool calls
                    if (aiMsg.toolExecutionRequests() != null && !aiMsg.toolExecutionRequests().isEmpty()) {
                        // Handle tool calls in response
                        List<Map<String, Object>> toolCalls = new ArrayList<>();
                        for (var toolRequest : aiMsg.toolExecutionRequests()) {
                            Map<String, Object> toolCall = new HashMap<>();
                            toolCall.put("id", toolRequest.id());
                            toolCall.put("type", "function");
                            
                            Map<String, Object> function = new HashMap<>();
                            function.put("name", toolRequest.name());
                            function.put("arguments", toolRequest.arguments());
                            toolCall.put("function", function);
                            
                            toolCalls.add(toolCall);
                        }
                        msg.put("tool_calls", toolCalls);
                        // Content might be null when there are tool calls
                        String content = aiMsg.text();
                        msg.put("content", content != null && !content.isEmpty() ? content : null);
                    } else {
                        msg.put("content", aiMsg.text());
                    }
                } else if (message instanceof SystemMessage) {
                    msg.put("role", "system");
                    msg.put("content", ((SystemMessage) message).text());
                } else if (message instanceof ToolExecutionResultMessage) {
                    // Handle tool execution results
                    ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) message;
                    msg.put("role", "tool");
                    msg.put("tool_call_id", toolMsg.id());
                    msg.put("content", toolMsg.text());
                } else {
                    // Handle other message types
                    msg.put("role", "system");
                    msg.put("content", message.toString());
                }
                
                openAiMessages.add(msg);
            }
            
            // Convert tool specifications to OpenAI tools format
            List<Map<String, Object>> tools = null;
            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                tools = new ArrayList<>();
                for (ToolSpecification spec : toolSpecifications) {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("type", "function");
                    
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", spec.name());
                    function.put("description", spec.description() != null ? spec.description() : "");
                    
                    // Convert parameters from ToolParameters
                    Map<String, Object> parameters = new HashMap<>();
                    ToolParameters toolParams = spec.parameters();
                    
                    if (toolParams != null) {
                        parameters.put("type", toolParams.type() != null ? toolParams.type() : "object");
                        
                        // ToolParameters.properties() returns Map<String, Map<String, Object>>
                        Map<String, Map<String, Object>> properties = toolParams.properties();
                        if (properties != null && !properties.isEmpty()) {
                            parameters.put("properties", properties);
                        } else {
                            parameters.put("properties", new HashMap<>());
                        }
                        
                        // ToolParameters.required() returns List<String>
                        List<String> required = toolParams.required();
                        if (required != null && !required.isEmpty()) {
                            parameters.put("required", required);
                        } else {
                            parameters.put("required", new ArrayList<>());
                        }
                    } else {
                        // Default empty parameters
                        parameters.put("type", "object");
                        parameters.put("properties", new HashMap<>());
                        parameters.put("required", new ArrayList<>());
                    }
                    
                    function.put("parameters", parameters);
                    tool.put("function", function);
                    tools.add(tool);
                }
            }
            
            // Build API request
            String url = baseUrl.endsWith("/") 
                    ? baseUrl + "chat/completions" 
                    : baseUrl + "/chat/completions";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", openAiMessages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("top_p", topP);
            requestBody.put("frequency_penalty", frequencyPenalty);
            requestBody.put("presence_penalty", presencePenalty);
            requestBody.put("seed", seed);
            
            // Add tools if available
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", tools);
            }
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", username);
            metadata.put("pwd", password);
            requestBody.put("metadata", metadata);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.debug("Calling LLM API with {} tools: {}", tools != null ? tools.size() : 0, url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            
            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            JsonNode choices = jsonResponse.get("choices");
            
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode choice = choices.get(0);
                JsonNode message = choice.get("message");
                
                if (message != null) {
                    // Check for tool calls in response
                    JsonNode toolCalls = message.get("tool_calls");
                    if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
                        // Handle tool calls
                        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
                        
                        for (JsonNode toolCall : toolCalls) {
                            String id = toolCall.get("id").asText();
                            JsonNode function = toolCall.get("function");
                            String name = function.get("name").asText();
                            String arguments = function.get("arguments").asText();
                            
                            toolExecutionRequests.add(
                                ToolExecutionRequest.builder()
                                    .id(id)
                                    .name(name)
                                    .arguments(arguments)
                                    .build()
                            );
                        }
                        
                        // Create AiMessage with tool execution requests
                        // If there's content, we'll include it, but AiMessage constructor
                        // doesn't support both text and tool requests together
                        // So we'll use toolExecutionRequests only
                        AiMessage aiMessage = new AiMessage(toolExecutionRequests);
                        return new Response<>(aiMessage);
                    } else {
                        // Regular text response
                        JsonNode content = message.get("content");
                        if (content != null && !content.isNull()) {
                            String responseText = content.asText();
                            AiMessage aiMessage = new AiMessage(responseText);
                            return new Response<>(aiMessage);
                        }
                    }
                }
            }
            
            throw new RuntimeException("Failed to parse API response");
            
        } catch (Exception e) {
            log.error("Error calling custom LLM API", e);
            throw new RuntimeException("Failed to generate AI response: " + e.getMessage(), e);
        }
    }
    
}

