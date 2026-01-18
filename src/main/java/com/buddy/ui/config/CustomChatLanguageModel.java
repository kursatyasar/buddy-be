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
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                
                // Debug: Log tool names being sent
                List<String> toolNames = toolSpecifications.stream()
                    .map(ToolSpecification::name)
                    .collect(java.util.stream.Collectors.toList());
                log.info("🔧 Sending {} tool(s) to LLM: {}", tools.size(), toolNames);
            } else {
                log.debug("⚠️ No tools provided to LLM");
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
            
            log.debug("📤 Calling LLM API with {} tools: {}", tools != null ? tools.size() : 0, url);
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
                        log.info("✅ LLM returned {} tool call(s)", toolCalls.size());
                        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
                        
                        for (JsonNode toolCall : toolCalls) {
                            String id = toolCall.get("id").asText();
                            JsonNode function = toolCall.get("function");
                            String name = function.get("name").asText();
                            String arguments = function.get("arguments").asText();
                            
                            log.info("🔨 Tool call: {} with arguments: {}", name, arguments);
                            
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
                        
                        // Extract token usage from response if available
                        TokenUsage tokenUsage = extractTokenUsage(jsonResponse);
                        
                        return Response.from(aiMessage, tokenUsage);
                    } else {
                        // Regular text response - but check for tool call in content
                        log.debug("📝 LLM returned text response (checking for tool call in content)");
                        JsonNode content = message.get("content");
                        if (content != null && !content.isNull()) {
                            String responseText = content.asText();
                            log.debug("Response text length: {} characters", responseText.length());
                            
                            // FALLBACK: Check if content contains tool call JSON
                            // Some LLMs return tool calls as JSON string in content instead of tool_calls array
                            ToolExecutionRequest parsedToolCall = tryParseToolCallFromContent(responseText, toolSpecifications);
                            
                            if (parsedToolCall != null) {
                                log.info("✅ FALLBACK: Successfully parsed tool call from content: {}", 
                                    parsedToolCall.name());
                                AiMessage aiMessage = new AiMessage(List.of(parsedToolCall));
                                
                                // Extract token usage from response if available
                                TokenUsage tokenUsage = extractTokenUsage(jsonResponse);
                                
                                return Response.from(aiMessage, tokenUsage);
                            }
                            
                            // Normal text response
                            AiMessage aiMessage = new AiMessage(responseText);
                            
                            // Extract token usage from response if available
                            TokenUsage tokenUsage = extractTokenUsage(jsonResponse);
                            
                            return Response.from(aiMessage, tokenUsage);
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
    
    /**
     * Fallback method to parse tool call from content if LLM returns it as JSON string
     * Handles cases where LLM doesn't support tool_calls array and returns tool call in content
     * Supports multiple formats:
     * 1. {"name": "createAccessRequest", "arguments": {...}}
     * 2. {"content": {"name": "...", "arguments": {...}}}
     * 3. {"portalName": "...", "reason": "..."} - Direct arguments for createAccessRequest
     */
    private ToolExecutionRequest tryParseToolCallFromContent(String content, List<ToolSpecification> toolSpecifications) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Try to parse as JSON object
            JsonNode contentJson = objectMapper.readTree(content.trim());
            
            // Format 1: Check if it's a tool call format: {"name": "...", "arguments": {...}}
            if (contentJson.has("name") && contentJson.has("arguments")) {
                String toolName = contentJson.get("name").asText();
                JsonNode argumentsNode = contentJson.get("arguments");
                
                log.info("🔧 FALLBACK: Found tool call in content - {} with arguments: {}", 
                    toolName, argumentsNode.toString());
                
                // Convert to ToolExecutionRequest
                String argumentsJson = argumentsNode.toString();
                String toolCallId = "call_" + System.currentTimeMillis(); // Generate ID
                
                return ToolExecutionRequest.builder()
                    .id(toolCallId)
                    .name(toolName)
                    .arguments(argumentsJson)
                    .build();
            }
            
            // Format 2: Check for nested format: {"content": {"name": "...", "arguments": {...}}}
            if (contentJson.has("content")) {
                JsonNode innerContent = contentJson.get("content");
                if (innerContent.isTextual()) {
                    // Recursive call for nested content
                    return tryParseToolCallFromContent(innerContent.asText(), toolSpecifications);
                } else if (innerContent.has("name") && innerContent.has("arguments")) {
                    String toolName = innerContent.get("name").asText();
                    JsonNode argumentsNode = innerContent.get("arguments");
                    
                    log.info("🔧 FALLBACK: Found nested tool call in content - {} with arguments: {}", 
                        toolName, argumentsNode.toString());
                    
                    String argumentsJson = argumentsNode.toString();
                    String toolCallId = "call_" + System.currentTimeMillis();
                    
                    return ToolExecutionRequest.builder()
                        .id(toolCallId)
                        .name(toolName)
                        .arguments(argumentsJson)
                        .build();
                }
            }
            
            // Format 3: Check if it's direct arguments format (e.g., {"portalName": "...", "reason": "..."})
            // This happens when LLM returns only the arguments without name field
            // We need to match it to available tools based on parameter names
            if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
                for (ToolSpecification spec : toolSpecifications) {
                    // Check if content matches this tool's parameters
                    if (matchesToolParameters(contentJson, spec)) {
                        String toolName = spec.name();
                        String argumentsJson = contentJson.toString();
                        
                        log.info("🔧 FALLBACK: Found direct arguments for tool {} - arguments: {}", 
                            toolName, argumentsJson);
                        
                        String toolCallId = "call_" + System.currentTimeMillis();
                        
                        return ToolExecutionRequest.builder()
                            .id(toolCallId)
                            .name(toolName)
                            .arguments(argumentsJson)
                            .build();
                    }
                }
            }
            
        } catch (Exception e) {
            // Not a JSON or not a tool call format
            log.debug("Content is not a tool call JSON: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract token usage from API response if available
     * Returns empty TokenUsage if not available to prevent NullPointerException
     */
    private TokenUsage extractTokenUsage(JsonNode jsonResponse) {
        try {
            JsonNode usage = jsonResponse.get("usage");
            if (usage != null) {
                int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
                int totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : (promptTokens + completionTokens);
                
                return new TokenUsage(promptTokens, completionTokens, totalTokens);
            }
        } catch (Exception e) {
            log.debug("Could not extract token usage from response: {}", e.getMessage());
        }
        
        // Return empty TokenUsage to prevent NullPointerException
        return new TokenUsage(0, 0, 0);
    }
    
    /**
     * Check if JSON content matches a tool's parameters
     * For createAccessRequest, checks if content has "portalName" and "reason" fields
     */
    private boolean matchesToolParameters(JsonNode contentJson, ToolSpecification spec) {
        if (contentJson == null || !contentJson.isObject()) {
            return false;
        }
        
        // Get tool parameters
        ToolParameters toolParams = spec.parameters();
        if (toolParams == null) {
            return false;
        }
        
        Map<String, Map<String, Object>> properties = toolParams.properties();
        if (properties == null || properties.isEmpty()) {
            return false;
        }
        
        // Check if all required parameters are present in content
        List<String> required = toolParams.required();
        if (required != null && !required.isEmpty()) {
            for (String paramName : required) {
                if (!contentJson.has(paramName)) {
                    return false;
                }
            }
        }
        
        // Check if content has at least some of the tool's parameters
        // This helps match createAccessRequest when content has portalName and reason
        int matchingParams = 0;
        for (String paramName : properties.keySet()) {
            if (contentJson.has(paramName)) {
                matchingParams++;
            }
        }
        
        // If most parameters match, consider it a match
        // For createAccessRequest with 2 params, both should match
        return matchingParams >= properties.size() * 0.8; // At least 80% of parameters match
    }
    
}

