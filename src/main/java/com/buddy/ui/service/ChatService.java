package com.buddy.ui.service;

import com.buddy.ui.model.Message;
import com.buddy.ui.model.SenderType;
import com.buddy.ui.model.dto.ChatRequest;
import com.buddy.ui.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    
    private final MessageRepository messageRepository;
    private final AiService aiService;
    
    @Transactional
    public Message processMessage(ChatRequest request) {
        log.info("Processing message for session: {}, user: {}", request.getSessionId(), request.getUserId());
        
        // Step 1: Save user message
        Message userMessage = Message.builder()
                .sessionId(request.getSessionId())
                .senderType(SenderType.USER)
                .content(request.getContent())
                .userId(request.getUserId())
                .build();
        
        userMessage = messageRepository.save(userMessage);
        log.debug("User message saved with ID: {}", userMessage.getId());
        
        // Step 2: Generate AI response
        Message aiMessage = aiService.generateResponse(request.getSessionId(), request.getContent());
        
        // Step 3: Save AI response
        aiMessage = messageRepository.save(aiMessage);
        log.debug("AI message saved with ID: {}", aiMessage.getId());
        
        // Step 4: Return AI message (as per requirements)
        return aiMessage;
    }
}

