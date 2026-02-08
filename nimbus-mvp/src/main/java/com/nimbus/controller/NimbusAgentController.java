package com.nimbus.controller;

import com.nimbus.agent.AgentService;
import com.nimbus.agent.ToolTrace;
import com.nimbus.model.City;
import com.nimbus.model.ClothingAdvice;
import com.nimbus.model.DailyWeather;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/nimbus/agent")
@RequiredArgsConstructor
public class NimbusAgentController {

    private final AgentService agentService;

    /**
     * Agent 模式对话接口。
     */
    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return ResponseEntity.badRequest().body(AgentChatResponse.error("message is required"));
        }

        AgentService.AgentResult result = agentService.chat(
                request.getSessionId(),
                request.getUserId(),
                request.getMessage()
        );

        AgentChatResponse response = new AgentChatResponse();
        response.setSuccess(result.isSuccess());
        response.setContent(result.getContent());
        response.setSessionId(result.getSessionId());
        response.setTraceId(result.getTraceId());
        response.setToolTrace(result.getToolTrace());
        response.setCity(result.getCity());
        response.setWeather(result.getWeather());
        response.setClothingAdvice(result.getClothingAdvice());
        response.setErrorMessage(result.getErrorMessage());
        return ResponseEntity.ok(response);
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
