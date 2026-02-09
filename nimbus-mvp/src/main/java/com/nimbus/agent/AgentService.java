package com.nimbus.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbus.client.QWeatherClient;
import com.nimbus.config.CityConfig;
import com.nimbus.model.City;
import com.nimbus.model.ClothingAdvice;
import com.nimbus.model.DailyWeather;
import com.nimbus.model.WeatherResponse;
import com.nimbus.service.ClothingAdviceService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Service
public class AgentService {

    // 最大步骤数，防止模型陷入死循环
    private static final int MAX_STEPS = 5;

    private final ChatClient agentChatClient;
    private final AgentSessionStore sessionStore;
    private final CityConfig cityConfig;
    private final QWeatherClient qWeatherClient;
    private final ClothingAdviceService clothingAdviceService;
    private final ObjectMapper objectMapper;

    public AgentService(@Qualifier("agentChatClient") ChatClient agentChatClient,
                        AgentSessionStore sessionStore,
                        CityConfig cityConfig,
                        QWeatherClient qWeatherClient,
                        ClothingAdviceService clothingAdviceService,
                        ObjectMapper objectMapper) {
        this.agentChatClient = agentChatClient;
        this.sessionStore = sessionStore;
        this.cityConfig = cityConfig;
        this.qWeatherClient = qWeatherClient;
        this.clothingAdviceService = clothingAdviceService;
        this.objectMapper = objectMapper;
    }

    public AgentResult chat(String sessionId, String userId, String message) {
        AgentSession session = sessionStore.getOrCreate(sessionId);
        sessionStore.append(session.getSessionId(), "user", message);

        // traceId 用于串联一次请求的工具调用记录
        String traceId = UUID.randomUUID().toString();
        List<ToolTrace> toolTraces = new ArrayList<>();
        AgentContext ctx = new AgentContext();

        for (int step = 0; step < MAX_STEPS; step++) {
            // 构造上下文交给模型决定下一步（工具调用 / 最终回复）
            String prompt = buildPrompt(session);
            String raw = agentChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            AgentAction action = parseAction(raw);
            if (action == null || !StringUtils.hasText(action.getType())) {
                return AgentResult.error("Invalid model output.", session.getSessionId(), traceId, toolTraces);
            }

            if ("final".equalsIgnoreCase(action.getType())) {
                // 最终回复直接返回
                String content = StringUtils.hasText(action.getContent()) ? action.getContent() : "";
                sessionStore.append(session.getSessionId(), "assistant", content);
                return AgentResult.success(content, session.getSessionId(), traceId, toolTraces, ctx);
            }

            if (!"tool".equalsIgnoreCase(action.getType())) {
                return AgentResult.error("Unknown action type.", session.getSessionId(), traceId, toolTraces);
            }

            ToolTrace trace = new ToolTrace();
            trace.setName(action.getName());
            trace.setStartTs(Instant.now().toEpochMilli());
            trace.setInputSummary(summarizeInput(action.getInput()));

            try {
                // 执行工具
                Object toolOutput = executeTool(action, ctx);
                trace.setStatus("success");
                trace.setOutputSummary(summarizeOutput(toolOutput));
                String toolMessage = toJson(toolOutput);
                // 将工具结果追加到会话中，供模型下一步读取
                sessionStore.append(session.getSessionId(), "tool", "[TOOL " + action.getName() + "] " + toolMessage);
            } catch (Exception e) {
                trace.setStatus("error");
                trace.setError(e.getMessage());
                String safeError = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
                sessionStore.append(session.getSessionId(), "tool",
                        "[TOOL_ERROR " + action.getName() + "] " + safeError);
            } finally {
                long end = Instant.now().toEpochMilli();
                trace.setDurationMs(Math.max(0, end - trace.getStartTs()));
            }

            toolTraces.add(trace);
        }

        return AgentResult.error("Agent max steps reached.", session.getSessionId(), traceId, toolTraces);
    }

    private Object executeTool(AgentAction action, AgentContext ctx) {
        String name = action.getName();
        Map<String, Object> input = action.getInput() != null ? action.getInput() : Map.of();

        if ("extract_city".equalsIgnoreCase(name)) {
            // 从文本中抽取城市
            String text = stringValue(input.get("text"));
            return extractCity(text);
        }

        if ("get_weather_today".equalsIgnoreCase(name)) {
            // 获取今日天气（支持 cityName 或 locationId）
            String cityName = stringValue(input.get("cityName"));
            String locationId = stringValue(input.get("locationId"));
            return getWeatherToday(cityName, locationId, ctx);
        }

        if ("get_clothing_advice".equalsIgnoreCase(name)) {
            // 根据天气生成穿搭建议
            Object weatherObj = input.get("dailyWeather");
            if (weatherObj == null) {
                weatherObj = input.get("weather");
            }
            DailyWeather weather = objectMapper.convertValue(weatherObj, DailyWeather.class);
            ClothingAdvice advice = clothingAdviceService.generateAdvice(weather);
            ctx.lastAdvice = advice;
            return advice;
        }

        throw new IllegalArgumentException("Unknown tool: " + name);
    }

    private Map<String, Object> extractCity(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(text)) {
            result.put("found", false);
            result.put("candidates", hotCityNames());
            return result;
        }

        Optional<City> fromText = cityConfig.extractFromText(text);
        if (fromText.isPresent()) {
            City city = fromText.get();
            result.put("found", true);
            result.put("cityName", city.getName());
            result.put("locationId", city.getLocationId());
            result.put("confidence", 0.9);
            return result;
        }

        Optional<City> byName = cityConfig.findByName(text);
        if (byName.isPresent()) {
            City city = byName.get();
            result.put("found", true);
            result.put("cityName", city.getName());
            result.put("locationId", city.getLocationId());
            result.put("confidence", 0.8);
            return result;
        }

        result.put("found", false);
        result.put("candidates", hotCityNames());
        return result;
    }

    private Map<String, Object> getWeatherToday(String cityName, String locationId, AgentContext ctx) {
        City city = null;
        if (StringUtils.hasText(cityName)) {
            city = cityConfig.findByName(cityName).orElse(null);
        }
        if (city == null && StringUtils.hasText(locationId)) {
            city = cityConfig.findByLocationId(locationId).orElse(null);
        }
        if (city == null) {
            throw new IllegalArgumentException("City not found.");
        }

        WeatherResponse response = qWeatherClient.get7DayForecast(city.getLocationId());
        if (response == null || !response.isSuccess()) {
            String code = response != null ? response.getCode() : "null";
            throw new IllegalStateException("Weather api failed: code=" + code);
        }
        DailyWeather today = response != null ? response.getToday() : null;
        if (today == null) {
            throw new IllegalStateException("No weather data.");
        }

        ctx.lastCity = city;
        ctx.lastWeather = today;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("weather", today);
        return result;
    }

    private List<String> hotCityNames() {
        List<String> names = new ArrayList<>();
        for (City city : cityConfig.getHotCities()) {
            names.add(city.getName());
        }
        return names;
    }

    private AgentAction parseAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        // 容错：从模型输出中截取 JSON
        String json = extractJson(raw);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentAction.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String extractJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        return null;
    }

    private String buildPrompt(AgentSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("SessionId: ").append(session.getSessionId()).append("\n");
        if (StringUtils.hasText(session.getSummary())) {
            sb.append("Summary: ").append(session.getSummary()).append("\n");
        }
        // 历史消息作为上下文
        sb.append("Conversation:\n");
        for (AgentMessage msg : session.getMessages()) {
            sb.append("- ").append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        sb.append("Now decide the next action.\n");
        return sb.toString();
    }

    private String summarizeInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            return input.toString();
        }
    }

    private String summarizeOutput(Object output) {
        if (output == null) {
            return "";
        }
        try {
            String json = objectMapper.writeValueAsString(output);
            return json.length() > 300 ? json.substring(0, 300) + "..." : json;
        } catch (JsonProcessingException e) {
            return output.toString();
        }
    }

    private String toJson(Object output) {
        if (output == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            return output.toString();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class AgentContext {
        // 最近一次工具调用结果缓存，用于响应补齐
        private City lastCity;
        private DailyWeather lastWeather;
        private ClothingAdvice lastAdvice;
    }

    public static class AgentResult {
        private final boolean success;
        private final String content;
        private final String sessionId;
        private final String traceId;
        private final List<ToolTrace> toolTrace;
        private final City city;
        private final DailyWeather weather;
        private final ClothingAdvice clothingAdvice;
        private final String errorMessage;

        private AgentResult(boolean success,
                            String content,
                            String sessionId,
                            String traceId,
                            List<ToolTrace> toolTrace,
                            City city,
                            DailyWeather weather,
                            ClothingAdvice clothingAdvice,
                            String errorMessage) {
            this.success = success;
            this.content = content;
            this.sessionId = sessionId;
            this.traceId = traceId;
            this.toolTrace = toolTrace;
            this.city = city;
            this.weather = weather;
            this.clothingAdvice = clothingAdvice;
            this.errorMessage = errorMessage;
        }

        public static AgentResult success(String content,
                                          String sessionId,
                                          String traceId,
                                          List<ToolTrace> toolTrace,
                                          AgentContext ctx) {
            return new AgentResult(true, content, sessionId, traceId, toolTrace,
                    ctx.lastCity, ctx.lastWeather, ctx.lastAdvice, null);
        }

        public static AgentResult error(String message,
                                        String sessionId,
                                        String traceId,
                                        List<ToolTrace> toolTrace) {
            return new AgentResult(false, null, sessionId, traceId, toolTrace,
                    null, null, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getContent() {
            return content;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getTraceId() {
            return traceId;
        }

        public List<ToolTrace> getToolTrace() {
            return toolTrace;
        }

        public City getCity() {
            return city;
        }

        public DailyWeather getWeather() {
            return weather;
        }

        public ClothingAdvice getClothingAdvice() {
            return clothingAdvice;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
