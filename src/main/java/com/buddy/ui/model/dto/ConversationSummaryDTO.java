package com.buddy.ui.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryDTO {
    private String sessionId;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Long messageCount;
    private String userId;
}

