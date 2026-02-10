package com.nimbus.agentai.user.service.impl;

import com.nimbus.agentai.user.mapper.ChatSessionMapper;
import com.nimbus.agentai.user.model.ChatSessionEntity;
import com.nimbus.agentai.user.model.UserEntity;
import com.nimbus.agentai.user.repository.ChatSessionRepository;
import com.nimbus.agentai.user.repository.UserRepository;
import com.nimbus.agentai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatSessionMapper chatSessionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserEntity getOrCreateUser(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            throw new IllegalArgumentException("userKey must not be blank");
        }
        String normalizedUserKey = userKey.trim();

        return userRepository.findByUserKey(normalizedUserKey)
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .userKey(normalizedUserKey)
                        .displayName(normalizedUserKey)
                        .status(1)
                        .build()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserEntity> findByUserKey(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return Optional.empty();
        }
        return userRepository.findByUserKey(userKey.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionEntity> listSessionsByUserKey(String userKey) {
        Optional<UserEntity> user = findByUserKey(userKey);
        if (user.isEmpty()) {
            return Collections.emptyList();
        }
        return chatSessionRepository.findByUserId(user.get().getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionEntity getOrCreateSession(String userKey, String sessionId, String memoryKey) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (!StringUtils.hasText(memoryKey)) {
            throw new IllegalArgumentException("memoryKey must not be blank");
        }

        UserEntity user = getOrCreateUser(userKey);

        return chatSessionRepository.findByUserIdAndSessionId(user.getId(), sessionId.trim())
                .map(existing -> {
                    chatSessionMapper.touch(user.getId(), sessionId.trim());
                    return existing;
                })
                .orElseGet(() -> chatSessionRepository.save(ChatSessionEntity.builder()
                        .sessionId(sessionId.trim())
                        .userId(user.getId())
                        .memoryKey(memoryKey.trim())
                        .lastActiveAt(LocalDateTime.now())
                        .build()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touchSession(String userKey, String sessionId) {
        if (!StringUtils.hasText(userKey) || !StringUtils.hasText(sessionId)) {
            return;
        }
        UserEntity user = getOrCreateUser(userKey);
        chatSessionMapper.touch(user.getId(), sessionId.trim());
    }
}
