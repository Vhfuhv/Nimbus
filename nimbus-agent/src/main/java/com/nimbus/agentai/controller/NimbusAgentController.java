package com.nimbus.agentai.controller;

import com.nimbus.agentai.agent.AgentRunContext;
import com.nimbus.agentai.agent.ToolTrace;
import com.nimbus.agentai.config.AgentAiConfig;
import com.nimbus.agentai.model.City;
import com.nimbus.agentai.model.ClothingAdvice;
import com.nimbus.agentai.model.DailyWeather;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/nimbus/agent")
@RequiredArgsConstructor
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

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(AgentAiConfig.TRACE_ID_KEY, traceId);
        toolContext.put(AgentAiConfig.TOOL_TRACE_KEY, toolTraces);
        toolContext.put(AgentAiConfig.RUN_CONTEXT_KEY, runContext);
        toolContext.put("userId", request.getUserId());

        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(sessionId);
        response.setTraceId(traceId);
        response.setToolTrace(toolTraces);

        try {
            String content = agentChatClientA.prompt()
                    .advisors(spec -> spec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                    .toolContext(toolContext)
                    .user(request.getMessage())
                    .call()
                    .content();

            response.setSuccess(true);
            response.setContent(content);
            response.setCity(runContext.getLastCity());
            response.setWeather(runContext.getLastWeather());
            response.setClothingAdvice(runContext.getLastAdvice());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            response.setCity(runContext.getLastCity());
            response.setWeather(runContext.getLastWeather());
            response.setClothingAdvice(runContext.getLastAdvice());
            return ResponseEntity.badRequest().body(response);
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
