package com.nimbus.agentai.user.repository.impl;

import com.nimbus.agentai.user.mapper.ChatSessionMapper;
import com.nimbus.agentai.user.model.ChatSessionEntity;
import com.nimbus.agentai.user.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatSessionRepositoryImpl implements ChatSessionRepository {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public Optional<ChatSessionEntity> findByUserIdAndSessionId(Long userId, String sessionId) {
        return Optional.ofNullable(chatSessionMapper.findByUserIdAndSessionId(userId, sessionId));
    }

    @Override
    public List<ChatSessionEntity> findByUserId(Long userId) {
        return chatSessionMapper.findByUserId(userId);
    }

    @Override
    public ChatSessionEntity save(ChatSessionEntity chatSession) {
        chatSessionMapper.insert(chatSession);
        return chatSession;
    }
}
