package com.nimbus.agent;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 SessionStore：简单可用，适合本地/单机演示。
 */
@Component
public class InMemoryAgentSessionStore implements AgentSessionStore {

    // 超过该条数时触发摘要
    private static final int MAX_MESSAGES = 10;
    // 摘要最大长度
    private static final int SUMMARY_MAX_CHARS = 800;
    // 会话超时
    private static final Duration TTL = Duration.ofHours(2);

    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    @Override
    public AgentSession getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        AgentSession session = sessions.computeIfAbsent(sessionId, AgentSession::new);
        session.touch();
        return session;
    }

    @Override
    public Optional<AgentSession> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (isExpired(session)) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        session.touch();
        return Optional.of(session);
    }

    @Override
    public void append(String sessionId, String role, String content) {
        AgentSession session = getOrCreate(sessionId);
        session.getMessages().add(new AgentMessage(role, content, Instant.now()));
        session.touch();
        summarizeIfNeeded(sessionId);
    }

    @Override
    public void summarizeIfNeeded(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (isExpired(session)) {
            sessions.remove(sessionId);
            return;
        }
        List<AgentMessage> messages = session.getMessages();
        if (messages.size() <= MAX_MESSAGES) {
            return;
        }

        int keepFrom = Math.max(0, messages.size() - MAX_MESSAGES);
        List<AgentMessage> toSummarize = new ArrayList<>(messages.subList(0, keepFrom));
        List<AgentMessage> keep = new ArrayList<>(messages.subList(keepFrom, messages.size()));

        String summary = buildSummary(toSummarize);
        session.setSummary(summary);
        messages.clear();
        messages.addAll(keep);
    }

    private boolean isExpired(AgentSession session) {
        Instant last = session.getLastAccess();
        return last != null && last.plus(TTL).isBefore(Instant.now());
    }

    private String buildSummary(List<AgentMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AgentMessage m : messages) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(m.getRole()).append(": ").append(safeClip(m.getContent(), 160));
            if (sb.length() > SUMMARY_MAX_CHARS) {
                break;
            }
        }
        return sb.toString();
    }

    private String safeClip(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
