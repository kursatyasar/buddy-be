package com.buddy.ui.service;

import com.buddy.ui.assistant.BuddyAssistant;
import com.buddy.ui.model.Message;
import com.buddy.ui.model.SenderType;
import com.buddy.ui.model.dto.ChatRequest;
import com.buddy.ui.model.dto.ConversationPageResponse;
import com.buddy.ui.model.dto.ConversationSummaryDTO;
import com.buddy.ui.model.dto.MessageResponseDTO;
import com.buddy.ui.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    
    private final MessageRepository messageRepository;
    private final BuddyAssistant buddyAssistant;
    
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
        
        // Step 2: Generate AI response using BuddyAssistant (LangChain4j)
        String aiResponseText = buddyAssistant.chat(request.getContent());
        log.debug("AI response generated: {}", aiResponseText);
        
        // Step 3: Create AI message from response
        Message aiMessage = Message.builder()
                .sessionId(request.getSessionId())
                .senderType(SenderType.AI)
                .content(aiResponseText)
                .userId(request.getUserId())
                .build();
        
        // Step 4: Save AI response
        aiMessage = messageRepository.save(aiMessage);
        log.debug("AI message saved with ID: {}", aiMessage.getId());
        
        // Step 5: Return AI message
        return aiMessage;
    }
    
    @Transactional(readOnly = true)
    public ConversationPageResponse getConversationsByUserId(String userId, int page, int size) {
        log.info("Fetching conversations for user: {}, page: {}, size: {}", userId, page, size);
        
        // Get all distinct session IDs for the user
        List<String> allSessionIds = messageRepository.findDistinctSessionIdsByUserId(userId);
        
        // Calculate pagination
        int totalElements = allSessionIds.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int start = page * size;
        int end = Math.min(start + size, totalElements);
        
        // Get paginated session IDs
        List<String> paginatedSessionIds = allSessionIds.subList(
            Math.min(start, totalElements), 
            Math.min(end, totalElements)
        );
        
        // Build conversation summaries
        List<ConversationSummaryDTO> conversations = paginatedSessionIds.stream()
                .map(sessionId -> {
                    // Get last message for this session
                    Pageable lastMessagePageable = PageRequest.of(0, 1);
                    List<Message> lastMessages = messageRepository.findMessagesByUserIdAndSessionIdOrderByCreatedAtDesc(
                            userId, sessionId, lastMessagePageable);
                    
                    Message lastMessage = lastMessages.isEmpty() ? null : lastMessages.get(0);
                    long messageCount = messageRepository.countMessagesByUserIdAndSessionId(userId, sessionId);
                    
                    return ConversationSummaryDTO.builder()
                            .sessionId(sessionId)
                            .lastMessage(lastMessage != null ? lastMessage.getContent() : "")
                            .lastMessageTime(lastMessage != null ? lastMessage.getCreatedAt() : null)
                            .messageCount(messageCount)
                            .userId(userId)
                            .build();
                })
                .sorted((c1, c2) -> {
                    // Sort by last message time (most recent first)
                    if (c1.getLastMessageTime() == null && c2.getLastMessageTime() == null) return 0;
                    if (c1.getLastMessageTime() == null) return 1;
                    if (c2.getLastMessageTime() == null) return -1;
                    return c2.getLastMessageTime().compareTo(c1.getLastMessageTime());
                })
                .collect(Collectors.toList());
        
        return ConversationPageResponse.builder()
                .conversations(conversations)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .build();
    }
    
    @Transactional(readOnly = true)
    public List<MessageResponseDTO> getMessagesBySessionId(String sessionId) {
        log.info("Fetching messages for session: {}", sessionId);
        
        List<Message> messages = messageRepository.findMessagesBySessionIdOrderByCreatedAtAsc(sessionId);
        
        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteConversation(String sessionId, String userId) {
        log.info("Deleting conversation for session: {}, user: {}", sessionId, userId);
        
        // Check if conversation exists and belongs to user
        long messageCount = messageRepository.countBySessionIdAndUserId(sessionId, userId);
        
        if (messageCount == 0) {
            throw new RuntimeException("Conversation not found or does not belong to user");
        }
        
        // Delete all messages in the conversation
        messageRepository.deleteBySessionIdAndUserId(sessionId, userId);
        
        log.info("Successfully deleted {} messages for session: {}", messageCount, sessionId);
    }
    
    private MessageResponseDTO convertToDTO(Message message) {
        return MessageResponseDTO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .senderType(message.getSenderType())
                .content(message.getContent())
                .userId(message.getUserId())
                .createdAt(message.getCreatedAt())
                .metadata(message.getMetadata())
                .build();
    }
}

