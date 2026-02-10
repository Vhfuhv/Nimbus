package com.nimbus.agentai.controller;

import com.nimbus.agentai.agent.AgentRunContext;
import com.nimbus.agentai.agent.ToolTrace;
import com.nimbus.agentai.config.AgentAiConfig;
import com.nimbus.agentai.model.City;
import com.nimbus.agentai.model.ClothingAdvice;
import com.nimbus.agentai.model.DailyWeather;
import com.nimbus.agentai.model.ForecastDayBrief;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/nimbus/agent")
@RequiredArgsConstructor
@Slf4j
public class NimbusAgentController {

    private final ChatClient agentChatClientA;

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return ResponseEntity.badRequest().body(AgentChatResponse.error("message is required"));
        }

        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();

        List<ToolTrace> toolTraces = new ArrayList<>();
        AgentRunContext runContext = new AgentRunContext();

        Map<String, Object> toolContext = buildToolContext(traceId, toolTraces, runContext, request);

        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId);
        response.setTraceId(traceId);
        response.setToolTrace(toolTraces);

        try {
            String content = agentChatClientA.prompt()
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .toolContext(toolContext)
                    .user(request.getMessage())
                    .call()
                    .content();

            response.setSuccess(true);
            response.setContent(content);
            response.setCity(runContext.getLastCity());
            response.setWeather(runContext.getLastWeather());
            response.setForecast(runContext.getLastForecast());
            response.setClothingAdvice(runContext.getLastAdvice());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            response.setCity(runContext.getLastCity());
            response.setWeather(runContext.getLastWeather());
            response.setForecast(runContext.getLastForecast());
            response.setClothingAdvice(runContext.getLastAdvice());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        if (request == null || !StringUtils.hasText(request.getMessage())) {
            sendEvent(emitter, "error", "message is required");
            emitter.complete();
            return emitter;
        }

        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();

        List<ToolTrace> toolTraces = new ArrayList<>();
        AgentRunContext runContext = new AgentRunContext();

        Map<String, Object> toolContext = buildToolContext(traceId, toolTraces, runContext, request);

        sendEvent(emitter, "meta", Map.of("sessionId", sessionId, "traceId", traceId));

        AtomicBoolean completed = new AtomicBoolean(false);
        StringBuilder contentBuilder = new StringBuilder(512);

        Flux<String> flux = agentChatClientA.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                .toolContext(toolContext)
                .user(request.getMessage())
                .stream()
                .content();

        Disposable disposable = flux.subscribe(
                delta -> {
                    if (!StringUtils.hasText(delta)) {
                        return;
                    }
                    contentBuilder.append(delta);
                    sendEvent(emitter, "delta", delta);
                },
                error -> {
                    if (completed.compareAndSet(false, true)) {
                        sendEvent(emitter, "error", error != null ? error.getMessage() : "error");
                        emitter.complete();
                    }
                },
                () -> {
                    if (completed.compareAndSet(false, true)) {
                        AgentChatResponse response = new AgentChatResponse();
                        response.setSuccess(true);
                        response.setContent(contentBuilder.toString());
                        response.setSessionId(sessionId);
                        response.setTraceId(traceId);
                        response.setToolTrace(toolTraces);
                        response.setCity(runContext.getLastCity());
                        response.setWeather(runContext.getLastWeather());
                        response.setForecast(runContext.getLastForecast());
                        response.setClothingAdvice(runContext.getLastAdvice());
                        sendEvent(emitter, "done", response);
                        emitter.complete();
                    }
                }
        );

        emitter.onTimeout(() -> {
            if (completed.compareAndSet(false, true)) {
                disposable.dispose();
                sendEvent(emitter, "error", "timeout");
                emitter.complete();
            }
        });
        emitter.onCompletion(() -> {
            if (completed.compareAndSet(false, true)) {
                disposable.dispose();
            }
        });

        return emitter;
    }

    @PostMapping(value = "/chat/stream/stable", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamStable(@RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        if (request == null || !StringUtils.hasText(request.getMessage())) {
            sendEvent(emitter, "error", "message is required");
            emitter.complete();
            return emitter;
        }

        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();

        List<ToolTrace> toolTraces = new ArrayList<>();
        AgentRunContext runContext = new AgentRunContext();

        Map<String, Object> toolContext = buildToolContext(traceId, toolTraces, runContext, request);

        sendEvent(emitter, "meta", Map.of("sessionId", sessionId, "traceId", traceId));

        AtomicBoolean completed = new AtomicBoolean(false);

        CompletableFuture.runAsync(() -> {
            try {
                String content = agentChatClientA.prompt()
                        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                        .toolContext(toolContext)
                        .user(request.getMessage())
                        .call()
                        .content();

                if (!StringUtils.hasText(content)) {
                    content = "";
                }

                pseudoStreamText(emitter, content, completed);

                if (completed.compareAndSet(false, true)) {
                    AgentChatResponse response = new AgentChatResponse();
                    response.setSuccess(true);
                    response.setContent(content);
                    response.setSessionId(sessionId);
                    response.setTraceId(traceId);
                    response.setToolTrace(toolTraces);
                    response.setCity(runContext.getLastCity());
                    response.setWeather(runContext.getLastWeather());
                    response.setForecast(runContext.getLastForecast());
                    response.setClothingAdvice(runContext.getLastAdvice());
                    sendEvent(emitter, "done", response);
                    emitter.complete();
                }
            } catch (Exception e) {
                if (completed.compareAndSet(false, true)) {
                    sendEvent(emitter, "error", e != null ? e.getMessage() : "error");
                    emitter.complete();
                }
            }
        });

        emitter.onTimeout(() -> {
            if (completed.compareAndSet(false, true)) {
                sendEvent(emitter, "error", "timeout");
                emitter.complete();
            }
        });
        emitter.onCompletion(() -> completed.compareAndSet(false, true));

        return emitter;
    }

    private static Map<String, Object> buildToolContext(String traceId,
                                                        List<ToolTrace> toolTraces,
                                                        AgentRunContext runContext,
                                                        AgentChatRequest request) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(AgentAiConfig.TRACE_ID_KEY, traceId);
        toolContext.put(AgentAiConfig.TOOL_TRACE_KEY, toolTraces);
        toolContext.put(AgentAiConfig.RUN_CONTEXT_KEY, runContext);
        toolContext.put("userMessage", request.getMessage());

        String userId = StringUtils.hasText(request.getUserId()) ? request.getUserId().trim() : "demo-user";
        toolContext.put("userId", userId);
        return toolContext;
    }

    private static void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.debug("SSE send failed. event={}, error={}", event, e.getMessage());
        }
    }

    private static void pseudoStreamText(SseEmitter emitter, String content, AtomicBoolean completed) {
        final int chunkSize = 16;
        for (int i = 0; i < content.length(); i += chunkSize) {
            if (completed.get()) {
                return;
            }
            int end = Math.min(content.length(), i + chunkSize);
            sendEvent(emitter, "delta", content.substring(i, end));
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Data
    public static class AgentChatRequest {
        private String sessionId;
        private String userId;
        private String message;
    }

    @Data
    public static class AgentChatResponse {
        private boolean success;
        private String content;
        private String sessionId;
        private String traceId;
        private List<ToolTrace> toolTrace;
        private City city;
        private DailyWeather weather;
        private List<ForecastDayBrief> forecast;
        private ClothingAdvice clothingAdvice;
        private String errorMessage;

        public static AgentChatResponse error(String message) {
            AgentChatResponse response = new AgentChatResponse();
            response.setSuccess(false);
            response.setErrorMessage(message);
            return response;
        }
    }
}
