package com.buddy.ui.service;

import com.buddy.ui.model.Message;
import com.buddy.ui.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {
    
    private final ChatClient chatClient;
    private final MessageRepository messageRepository;
    
    private static final int CONTEXT_WINDOW_SIZE = 10; // Last N messages for context
    
    public Message generateResponse(String sessionId, String userMessage) {
        log.debug("Generating AI response for session: {}", sessionId);
        
        // Fetch conversation history for context
        Pageable pageable = PageRequest.of(0, CONTEXT_WINDOW_SIZE);
        List<Message> recentMessages = messageRepository.findLastMessagesBySessionId(sessionId, pageable);
        
        // Build conversation context
        String conversationContext = buildConversationContext(recentMessages);
        
        // Generate AI response
        String aiResponse = chatClient.prompt()
                .user(conversationContext + "\n\nUser: " + userMessage + "\n\nBuddy:")
                .call()
                .content();
        
        log.debug("AI response generated successfully");
        
        // Create AI message entity
        Message aiMessage = Message.builder()
                .sessionId(sessionId)
                .senderType(com.buddy.ui.model.SenderType.AI)
                .content(aiResponse)
                .metadata(createMetadata())
                .build();
        
        return aiMessage;
    }
    
    private String buildConversationContext(List<Message> messages) {
        if (messages.isEmpty()) {
            return "This is the start of a new conversation.";
        }
        
        // Reverse to get chronological order
        List<Message> chronological = messages.stream()
                .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
                .collect(Collectors.toList());
        
        StringBuilder context = new StringBuilder("Previous conversation:\n");
        for (Message msg : chronological) {
            String sender = msg.getSenderType() == com.buddy.ui.model.SenderType.USER ? "User" : "Buddy";
            context.append(sender).append(": ").append(msg.getContent()).append("\n");
        }
        
        return context.toString();
    }
    
    private Map<String, Object> createMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("model", "gpt-4o-mini");
        metadata.put("timestamp", System.currentTimeMillis());
        return metadata;
    }
}

