package com.nimbus.agentai.user.service;

import com.nimbus.agentai.user.model.ChatSessionEntity;
import com.nimbus.agentai.user.model.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserService {

    UserEntity getOrCreateUser(String userKey);

    Optional<UserEntity> findByUserKey(String userKey);

    List<ChatSessionEntity> listSessionsByUserKey(String userKey);

    ChatSessionEntity getOrCreateSession(String userKey, String sessionId, String memoryKey);

    void touchSession(String userKey, String sessionId);
}
