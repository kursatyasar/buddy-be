package com.buddy.ui.repository;

import com.buddy.ui.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    
    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<Message> findLastMessagesBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
}

