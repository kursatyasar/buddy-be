package com.buddy.ui.model.dto;

import com.buddy.ui.model.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDTO {
    private UUID id;
    private String sessionId;
    private SenderType senderType;
    private String content;
    private String userId;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;
}


