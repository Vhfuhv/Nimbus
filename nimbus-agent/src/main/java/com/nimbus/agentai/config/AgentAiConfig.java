package com.nimbus.agentai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbus.agentai.agent.AgentRunContext;
import com.nimbus.agentai.agent.ToolTrace;
import com.nimbus.agentai.client.QWeatherClient;
import com.nimbus.agentai.model.City;
import com.nimbus.agentai.model.ClothingAdvice;
import com.nimbus.agentai.model.DailyWeather;
import com.nimbus.agentai.model.ForecastDayBrief;
import com.nimbus.agentai.model.WeatherResponse;
import com.nimbus.agentai.service.ClothingAdviceService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
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

    private static final List<Integer> SUPPORTED_FORECAST_DAYS = List.of(3, 7, 10, 15, 30);

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    @Bean(name = "agentChatClientA")
    public ChatClient agentChatClient(ChatClient.Builder builder,
                                      ChatMemory chatMemory,
                                      List<ToolCallback> agentTools) {
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return builder
                .defaultAdvisors(memoryAdvisor)
                .defaultSystem("""
                        你是 Nimbus 的天气/穿搭 Agent。
                        你可以调用工具获取真实数据，严禁编造天气或城市编码。
                        工具列表：
                        - extract_city: 从用户文本识别城市（返回 cityName/locationId 或候选）
                        - get_weather_today: 获取城市天气预报（支持 days=3/7/10/15/30，dayOffset=0 今天，1 明天...；返回 weather + forecast 列表）
                        - get_clothing_advice: 根据指定日期天气生成结构化穿搭建议
                        规则：
                        1) 如果城市缺失，优先调用 extract_city；如果 extract_city.found=false，则先追问城市（可给 candidates 提示）；
                        2) 用户问“未来3天/7天/10天/15天/30天”时，调用 get_weather_today 并设置对应 days；用户问“明天/后天”等，设置 dayOffset；
                        3) 拿到 cityName/locationId 后调用 get_weather_today；如果 get_weather_today.success=false，则解释原因并追问/建议更换城市或稍后重试；
                        4) 拿到 weather 后调用 get_clothing_advice；
                        5) 最终用中文、简洁、可执行的方式回复（包含：天气概况、最高/最低温、穿搭、是否带伞；如果用户问未来N天则给出N天简表）。
                        """)
                .defaultToolCallbacks(agentTools.toArray(ToolCallback[]::new))
                .build();
    }

    @Bean
    public ToolCallback extractCityTool(CityConfig cityConfig, ObjectMapper objectMapper) {
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
                traceOutput(trace, objectMapper, output);
                return output;
            } finally {
                traceEnd(trace);
            }
        };

        ToolCallback raw = FunctionToolCallback.builder("extract_city", fn)
                .description("从用户文本中识别城市，返回 cityName 与 locationId。")
                .inputType(ExtractCityInput.class)
                .build();
        return new SafeToolCallback(raw);
    }

    @Bean
    public ToolCallback getWeatherTodayTool(CityConfig cityConfig,
                                                QWeatherClient qWeatherClient,
                                                ObjectMapper objectMapper) {
        BiFunction<GetWeatherTodayInput, ToolContext, WeatherToolOutput> fn = (input, toolContext) -> {
            ToolTrace trace = traceStart(toolContext, "get_weather_today", objectMapper, input);
            try {
                GetWeatherTodayInput normalizedInput = normalizeWeatherInput(cityConfig, toolContext, input);
                WeatherToolOutput output = getWeatherToday(cityConfig, qWeatherClient, normalizedInput);

                AgentRunContext runContext = getOrCreateRunContext(toolContext);
                if (output.success() && output.city() != null && output.weather() != null) {
                    runContext.setLastCity(output.city());
                    runContext.setLastWeather(output.weather());
                    runContext.setLastForecast(output.forecast());
                }

                traceSuccess(trace, objectMapper, output);
                return output;
            } catch (Exception e) {
                WeatherToolOutput output = WeatherToolOutput.error(safeMessage(e));
                traceError(trace, e);
                traceOutput(trace, objectMapper, output);
                return output;
            } finally {
                traceEnd(trace);
            }
        };

        ToolCallback raw = FunctionToolCallback.builder("get_weather_today", fn)
                .description("获取城市天气预报。输入 cityName/locationId，可选 days(3/7/10/15/30) 与 dayOffset(0今天,1明天...).")
                .inputType(GetWeatherTodayInput.class)
                .build();
        return new SafeToolCallback(raw);
    }

    private static GetWeatherTodayInput normalizeWeatherInput(CityConfig cityConfig, ToolContext toolContext, GetWeatherTodayInput input) {
        if (input != null && (StringUtils.hasText(input.cityName()) || StringUtils.hasText(input.locationId()))) {
            return input;
        }

        String text = null;
        Map<String, Object> ctx = toolContext != null ? toolContext.getContext() : null;
        if (ctx != null) {
            Object userMessage = ctx.get("userMessage");
            if (userMessage instanceof String s && StringUtils.hasText(s)) {
                text = s;
            }
        }

        if (!StringUtils.hasText(text)) {
            return input;
        }

        Optional<City> fromText = cityConfig.extractFromText(text);
        if (fromText.isPresent()) {
            City city = fromText.get();
            return new GetWeatherTodayInput(city.getName(), city.getLocationId(), input != null ? input.days() : null, input != null ? input.dayOffset() : null);
        }

        Optional<City> byName = cityConfig.findByName(text);
        if (byName.isPresent()) {
            City city = byName.get();
            return new GetWeatherTodayInput(city.getName(), city.getLocationId(), input != null ? input.days() : null, input != null ? input.dayOffset() : null);
        }

        return input;
    }

    @Bean
    public ToolCallback getClothingAdviceTool(ClothingAdviceService clothingAdviceService,
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
                traceOutput(trace, objectMapper, advice);
                return advice;
            } finally {
                traceEnd(trace);
            }
        };

        ToolCallback raw = FunctionToolCallback.builder("get_clothing_advice", fn)
                .description("根据指定日期的天气生成结构化穿搭建议。输入 dailyWeather。")
                .inputType(GetClothingAdviceInput.class)
                .build();
        return new SafeToolCallback(raw);
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
        Integer daysInput = input != null ? input.days() : null;
        Integer dayOffsetInput = input != null ? input.dayOffset() : null;

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
        int dayOffset = normalizeDayOffset(dayOffsetInput);
        if (dayOffset > 29) {
            return WeatherToolOutput.error("dayOffset too large (max 29 for 30-day forecast).");
        }
        int days = normalizeForecastDays(daysInput, dayOffset);
        try {
            response = qWeatherClient.getForecast(city.getLocationId(), days);
        } catch (Exception e) {
            return WeatherToolOutput.error("Weather api exception: " + safeMessage(e));
        }
        if (response == null || !response.isSuccess()) {
            String code = response != null ? response.getCode() : "null";
            return WeatherToolOutput.error("Weather api failed: code=" + code);
        }
        DailyWeather targetDay = getDayByOffset(response, dayOffset);
        if (targetDay == null) {
            return WeatherToolOutput.error("No weather data.");
        }
        List<ForecastDayBrief> forecast = daysInput != null ? toForecastBrief(response.getDaily()) : Collections.emptyList();
        return WeatherToolOutput.success(city, targetDay, forecast);
    }

    private static int normalizeDayOffset(Integer dayOffset) {
        if (dayOffset == null) {
            return 0;
        }
        return Math.max(0, dayOffset);
    }

    private static int normalizeForecastDays(Integer requestedDays, int dayOffset) {
        int minRequired = Math.max(1, dayOffset + 1);

        Integer normalizedRequested = requestedDays;
        if (normalizedRequested == null) {
            normalizedRequested = 0;
        }
        if (normalizedRequested < minRequired) {
            normalizedRequested = minRequired;
        }

        for (Integer supported : SUPPORTED_FORECAST_DAYS) {
            if (supported >= normalizedRequested) {
                return supported;
            }
        }
        return 30;
    }

    private static DailyWeather getDayByOffset(WeatherResponse response, int dayOffset) {
        if (response == null) {
            return null;
        }
        List<DailyWeather> daily = response.getDaily();
        if (daily == null || daily.isEmpty()) {
            return null;
        }
        if (dayOffset < 0 || dayOffset >= daily.size()) {
            return null;
        }
        return daily.get(dayOffset);
    }

    private static List<ForecastDayBrief> toForecastBrief(List<DailyWeather> daily) {
        if (daily == null || daily.isEmpty()) {
            return Collections.emptyList();
        }
        List<ForecastDayBrief> brief = new ArrayList<>(daily.size());
        for (DailyWeather d : daily) {
            if (d == null) {
                continue;
            }
            brief.add(new ForecastDayBrief(d.getFxDate(), d.getTextDay(), d.getTempMin(), d.getTempMax(), d.getPrecip()));
        }
        return brief;
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

    private static void traceOutput(ToolTrace trace, ObjectMapper objectMapper, Object output) {
        if (trace == null) {
            return;
        }
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

    public record GetWeatherTodayInput(String cityName, String locationId, Integer days, Integer dayOffset) {}

    public record WeatherToolOutput(boolean success,
                                    City city,
                                    DailyWeather weather,
                                    List<ForecastDayBrief> forecast,
                                    String errorMessage) {
        public static WeatherToolOutput success(City city, DailyWeather weather) {
            return new WeatherToolOutput(true, city, weather, Collections.emptyList(), null);
        }

        public static WeatherToolOutput success(City city, DailyWeather weather, List<ForecastDayBrief> forecast) {
            return new WeatherToolOutput(true, city, weather, forecast != null ? forecast : Collections.emptyList(), null);
        }

        public static WeatherToolOutput error(String errorMessage) {
            return new WeatherToolOutput(false, null, null, Collections.emptyList(), errorMessage);
        }
    }

    public record GetClothingAdviceInput(DailyWeather dailyWeather) {}
}
