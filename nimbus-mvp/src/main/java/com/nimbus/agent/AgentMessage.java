package com.nimbus.agent;

import java.time.Instant;

/**
 * 会话中的一条消息记录。
 */
public class AgentMessage {
    private String role;
    private String content;
    private Instant timestamp;

    public AgentMessage() {
    }

    public AgentMessage(String role, String content, Instant timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
