package com.buddy.ui.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteConversationRequest {
    
    @NotBlank(message = "userId is required")
    private String userId;
}

