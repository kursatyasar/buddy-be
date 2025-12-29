package com.buddy.ui.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationPageResponse {
    private List<ConversationSummaryDTO> conversations;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private int size;
}

