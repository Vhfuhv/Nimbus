package com.nimbus.agentai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbus.agentai.agent.AgentRunContext;
import com.nimbus.agentai.agent.ToolTrace;
import com.nimbus.agentai.client.QWeatherClient;
import com.nimbus.agentai.model.City;
import com.nimbus.agentai.model.ClothingAdvice;
import com.nimbus.agentai.model.DailyWeather;
import com.nimbus.agentai.model.WeatherResponse;
import com.nimbus.agentai.service.ClothingAdviceService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;

@Configuration
public class AgentAiConfig {

    public static final String TOOL_TRACE_KEY = "toolTrace";
    public static final String RUN_CONTEXT_KEY = "runContext";
    public static final String TRACE_ID_KEY = "traceId";

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean(name = "agentChatClientA")
    public ChatClient agentChatClient(ChatClient.Builder builder,
                                      ChatMemory chatMemory,
                                      List<FunctionCallback> agentTools) {
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .withChatMemoryRetrieveSize(20)
                .build();

        return builder
                .defaultAdvisors(memoryAdvisor)
                .defaultSystem("""
                        你是 Nimbus 的天气/穿搭 Agent。
                        你可以调用工具获取真实数据，严禁编造天气或城市编码。
                        工具列表：
                        - extract_city: 从用户文本识别城市（返回 cityName/locationId 或候选）
                        - get_weather_today: 获取城市今日天气（输入 cityName 或 locationId）
                        - get_clothing_advice: 根据今日天气生成结构化穿搭建议
                        规则：
                        1) 如果城市缺失，优先调用 extract_city；如果 extract_city.found=false，则先追问城市（可给 candidates 提示）；
                        2) 拿到 cityName/locationId 后调用 get_weather_today；如果 get_weather_today.success=false，则解释原因并追问/建议更换城市或稍后重试；
                        3) 拿到 weather 后调用 get_clothing_advice；
                        4) 最终用中文、简洁、可执行的方式回复（包含：天气概况、最高/最低温、穿搭、是否带伞）。
                        """)
                .defaultFunctions(agentTools.toArray(FunctionCallback[]::new))
                .build();
    }

    @Bean
    public FunctionCallback extractCityTool(CityConfig cityConfig, ObjectMapper objectMapper) {
        BiFunction<ExtractCityInput, ToolContext, ExtractCityOutput> fn = (input, toolContext) -> {
            ToolTrace trace = traceStart(toolContext, "extract_city", objectMapper, input);
            try {
                String text = input != null ? input.text() : null;
                ExtractCityOutput output = extractCity(cityConfig, text);
                traceSuccess(trace, objectMapper, output);
                return output;
            } catch (Exception e) {
                ExtractCityOutput output = ExtractCityOutput.error(safeMessage(e), hotCityNames(cityConfig));
                traceError(trace, e);
                traceSuccess(trace, objectMapper, output);
                return output;
            } finally {
                traceEnd(trace);
            }
        };

        return FunctionCallbackWrapper.builder(fn)
                .withName("extract_city")
                .withDescription("从用户文本中识别城市，返回 cityName 与 locationId。")
                .withInputType(ExtractCityInput.class)
                .withObjectMapper(objectMapper)
                .build();
    }

    @Bean
    public FunctionCallback getWeatherTodayTool(CityConfig cityConfig,
                                                QWeatherClient qWeatherClient,
                                                ObjectMapper objectMapper) {
        BiFunction<GetWeatherTodayInput, ToolContext, WeatherToolOutput> fn = (input, toolContext) -> {
            ToolTrace trace = traceStart(toolContext, "get_weather_today", objectMapper, input);
            try {
                WeatherToolOutput output = getWeatherToday(cityConfig, qWeatherClient, input);

                AgentRunContext runContext = getOrCreateRunContext(toolContext);
                if (output.success() && output.city() != null && output.weather() != null) {
                    runContext.setLastCity(output.city());
                    runContext.setLastWeather(output.weather());
                }

                traceSuccess(trace, objectMapper, output);
                return output;
            } catch (Exception e) {
                WeatherToolOutput output = WeatherToolOutput.error(safeMessage(e));
                traceError(trace, e);
                traceSuccess(trace, objectMapper, output);
                return output;
            } finally {
                traceEnd(trace);
            }
        };

        return FunctionCallbackWrapper.builder(fn)
                .withName("get_weather_today")
                .withDescription("获取指定城市的今日天气。输入 cityName 或 locationId。")
                .withInputType(GetWeatherTodayInput.class)
                .withObjectMapper(objectMapper)
                .build();
    }

    @Bean
    public FunctionCallback getClothingAdviceTool(ClothingAdviceService clothingAdviceService,
                                                  ObjectMapper objectMapper) {
        BiFunction<GetClothingAdviceInput, ToolContext, ClothingAdvice> fn = (input, toolContext) -> {
            ToolTrace trace = traceStart(toolContext, "get_clothing_advice", objectMapper, input);
            try {
                DailyWeather weather = input != null ? input.dailyWeather() : null;
                ClothingAdvice advice = clothingAdviceService.generateAdvice(weather);

                AgentRunContext runContext = getOrCreateRunContext(toolContext);
                runContext.setLastAdvice(advice);

                traceSuccess(trace, objectMapper, advice);
                return advice;
            } catch (Exception e) {
                ClothingAdvice advice = ClothingAdvice.builder()
                        .overview("生成穿搭建议失败")
                        .reminder(safeMessage(e))
                        .build();
                traceError(trace, e);
                traceSuccess(trace, objectMapper, advice);
                return advice;
            } finally {
                traceEnd(trace);
            }
        };

        return FunctionCallbackWrapper.builder(fn)
                .withName("get_clothing_advice")
                .withDescription("根据今日天气生成结构化穿搭建议。输入 dailyWeather。")
                .withInputType(GetClothingAdviceInput.class)
                .withObjectMapper(objectMapper)
                .build();
    }

    private static ExtractCityOutput extractCity(CityConfig cityConfig, String text) {
        if (!StringUtils.hasText(text)) {
            return ExtractCityOutput.notFound(hotCityNames(cityConfig));
        }

        Optional<City> fromText = cityConfig.extractFromText(text);
        if (fromText.isPresent()) {
            City city = fromText.get();
            return ExtractCityOutput.found(city.getName(), city.getLocationId(), 0.9);
        }

        Optional<City> byName = cityConfig.findByName(text);
        if (byName.isPresent()) {
            City city = byName.get();
            return ExtractCityOutput.found(city.getName(), city.getLocationId(), 0.8);
        }

        return ExtractCityOutput.notFound(hotCityNames(cityConfig));
    }

    private static WeatherToolOutput getWeatherToday(CityConfig cityConfig,
                                                     QWeatherClient qWeatherClient,
                                                     GetWeatherTodayInput input) {
        String cityName = input != null ? input.cityName() : null;
        String locationId = input != null ? input.locationId() : null;

        City city = null;
        if (StringUtils.hasText(cityName)) {
            city = cityConfig.findByName(cityName).orElse(null);
        }
        if (city == null && StringUtils.hasText(locationId)) {
            city = cityConfig.findByLocationId(locationId).orElse(null);
        }
        if (city == null) {
            return WeatherToolOutput.error("City not found.");
        }

        WeatherResponse response;
        try {
            response = qWeatherClient.get7DayForecast(city.getLocationId());
        } catch (Exception e) {
            return WeatherToolOutput.error("Weather api exception: " + safeMessage(e));
        }
        if (response == null || !response.isSuccess()) {
            String code = response != null ? response.getCode() : "null";
            return WeatherToolOutput.error("Weather api failed: code=" + code);
        }
        DailyWeather today = response.getToday();
        if (today == null) {
            return WeatherToolOutput.error("No weather data.");
        }
        return WeatherToolOutput.success(city, today);
    }

    private static List<String> hotCityNames(CityConfig cityConfig) {
        List<String> names = new ArrayList<>();
        for (City city : cityConfig.getHotCities()) {
            names.add(city.getName());
        }
        return names;
    }

    private static AgentRunContext getOrCreateRunContext(ToolContext toolContext) {
        Map<String, Object> ctx = toolContext != null ? toolContext.getContext() : null;
        if (ctx == null) {
            return new AgentRunContext();
        }
        Object existing = ctx.get(RUN_CONTEXT_KEY);
        if (existing instanceof AgentRunContext runContext) {
            return runContext;
        }
        AgentRunContext runContext = new AgentRunContext();
        ctx.put(RUN_CONTEXT_KEY, runContext);
        return runContext;
    }

    private static ToolTrace traceStart(ToolContext toolContext, String name, ObjectMapper objectMapper, Object input) {
        ToolTrace trace = new ToolTrace();
        trace.setName(name);
        trace.setStartTs(Instant.now().toEpochMilli());
        trace.setStatus("running");
        trace.setInputSummary(safeJson(objectMapper, input, 300));

        Map<String, Object> ctx = toolContext != null ? toolContext.getContext() : null;
        if (ctx != null) {
            Object traceList = ctx.get(TOOL_TRACE_KEY);
            if (traceList instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<ToolTrace> traces = (List<ToolTrace>) list;
                traces.add(trace);
            }
        }
        return trace;
    }

    private static void traceSuccess(ToolTrace trace, ObjectMapper objectMapper, Object output) {
        if (trace == null) {
            return;
        }
        trace.setStatus("success");
        trace.setOutputSummary(safeJson(objectMapper, output, 300));
    }

    private static void traceError(ToolTrace trace, Exception e) {
        if (trace == null) {
            return;
        }
        trace.setStatus("error");
        trace.setError(e != null && StringUtils.hasText(e.getMessage()) ? e.getMessage() : "error");
    }

    private static void traceEnd(ToolTrace trace) {
        if (trace == null) {
            return;
        }
        long end = Instant.now().toEpochMilli();
        trace.setDurationMs(Math.max(0, end - trace.getStartTs()));
    }

    private static String safeJson(ObjectMapper objectMapper, Object value, int max) {
        if (value == null) {
            return "";
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() <= max) {
                return json;
            }
            return json.substring(0, max) + "...";
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String safeMessage(Exception e) {
        if (e == null) {
            return "error";
        }
        if (StringUtils.hasText(e.getMessage())) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    public record ExtractCityInput(String text) {}

    public record ExtractCityOutput(boolean found,
                                    String cityName,
                                    String locationId,
                                    Double confidence,
                                    List<String> candidates,
                                    String errorMessage) {
        public static ExtractCityOutput found(String cityName, String locationId, double confidence) {
            return new ExtractCityOutput(true, cityName, locationId, confidence, null, null);
        }

        public static ExtractCityOutput notFound(List<String> candidates) {
            return new ExtractCityOutput(false, null, null, null, candidates, null);
        }

        public static ExtractCityOutput error(String errorMessage, List<String> candidates) {
            return new ExtractCityOutput(false, null, null, null, candidates, errorMessage);
        }
    }

    public record GetWeatherTodayInput(String cityName, String locationId) {}

    public record WeatherToolOutput(boolean success, City city, DailyWeather weather, String errorMessage) {
        public static WeatherToolOutput success(City city, DailyWeather weather) {
            return new WeatherToolOutput(true, city, weather, null);
        }

        public static WeatherToolOutput error(String errorMessage) {
            return new WeatherToolOutput(false, null, null, errorMessage);
        }
    }

    public record GetClothingAdviceInput(DailyWeather dailyWeather) {}
}
