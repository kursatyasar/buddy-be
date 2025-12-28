package com.buddy.ui.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    @NotBlank(message = "sessionId is required")
    private String sessionId;
    
    @NotBlank(message = "content is required")
    private String content;
    
    @NotBlank(message = "userId is required")
    private String userId;
}

