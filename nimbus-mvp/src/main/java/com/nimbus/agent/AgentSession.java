package com.nimbus.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话上下文（消息列表 + 摘要 + 过期控制）。
 */
public class AgentSession {
    private final String sessionId;
    private final List<AgentMessage> messages = new ArrayList<>();
    private String summary;
    private Instant lastAccess;

    public AgentSession(String sessionId) {
        this.sessionId = sessionId;
        this.lastAccess = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getLastAccess() {
        return lastAccess;
    }

    public void touch() {
        this.lastAccess = Instant.now();
    }
}
