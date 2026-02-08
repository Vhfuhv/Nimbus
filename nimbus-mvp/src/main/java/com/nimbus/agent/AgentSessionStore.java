package com.nimbus.agent;

import java.util.Optional;

/**
 * 会话存储抽象。
 */
public interface AgentSessionStore {
    AgentSession getOrCreate(String sessionId);

    Optional<AgentSession> get(String sessionId);

    void append(String sessionId, String role, String content);

    void summarizeIfNeeded(String sessionId);
}
