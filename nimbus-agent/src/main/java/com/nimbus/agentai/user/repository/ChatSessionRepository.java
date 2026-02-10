package com.nimbus.agentai.user.repository;

import com.nimbus.agentai.user.model.ChatSessionEntity;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository {

    Optional<ChatSessionEntity> findByUserIdAndSessionId(Long userId, String sessionId);

    List<ChatSessionEntity> findByUserId(Long userId);

    ChatSessionEntity save(ChatSessionEntity chatSession);
}
