package com.buddy.ui.controller;

import com.buddy.ui.model.Message;
import com.buddy.ui.model.dto.ChatRequest;
import com.buddy.ui.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final ChatService chatService;
    
    @PostMapping("/send")
    public ResponseEntity<Message> sendMessage(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request for session: {}", request.getSessionId());
        
        Message response = chatService.processMessage(request);
        
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}

