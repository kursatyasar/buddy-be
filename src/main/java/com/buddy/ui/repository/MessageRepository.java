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
    
    
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<Message> findLastMessagesBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
    
    @Query("SELECT DISTINCT m.sessionId FROM Message m WHERE m.userId = :userId")
    List<String> findDistinctSessionIdsByUserId(@Param("userId") String userId);
    
    @Query("SELECT COUNT(DISTINCT m.sessionId) FROM Message m WHERE m.userId = :userId")
    long countDistinctSessionIdsByUserId(@Param("userId") String userId);
    
    @Query("SELECT m FROM Message m WHERE m.userId = :userId AND m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<Message> findMessagesByUserIdAndSessionIdOrderByCreatedAtDesc(@Param("userId") String userId, @Param("sessionId") String sessionId, Pageable pageable);
    
    @Query("SELECT COUNT(m) FROM Message m WHERE m.userId = :userId AND m.sessionId = :sessionId")
    long countMessagesByUserIdAndSessionId(@Param("userId") String userId, @Param("sessionId") String sessionId);
    
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId ORDER BY m.createdAt ASC")
    List<Message> findMessagesBySessionIdOrderByCreatedAtAsc(@Param("sessionId") String sessionId);
}

