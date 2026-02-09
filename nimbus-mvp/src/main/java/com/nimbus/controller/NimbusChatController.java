package com.nimbus.controller;

import com.nimbus.config.CityConfig;
import com.nimbus.model.City;
import com.nimbus.model.ClothingAdvice;
import com.nimbus.model.DailyWeather;
import com.nimbus.service.WeatherQueryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 自然语言对话接口（Spring AI）
 */
@Slf4j
@RestController
@RequestMapping("/nimbus")
@RequiredArgsConstructor
public class NimbusChatController {

    @Qualifier("chatClient")
    private final ChatClient chatClient;
    private final WeatherQueryService weatherQueryService;
    private final CityConfig cityConfig;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            if (request == null || !StringUtils.hasText(request.getMessage())) {
                return ResponseEntity.badRequest().body(ChatResponse.error("message 不能为空"));
            }

            City city = resolveCity(request)
                    .orElseThrow(() -> new RuntimeException("未识别到城市，请在对话中包含城市名（如：北京今天穿什么？）"));

            WeatherQueryService.WeatherResult result = weatherQueryService.queryToday(city);

            String prompt = buildPrompt(request.getMessage(), result.getCity(), result.getWeather(), result.getAdvice());
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setContent(content);
            response.setCity(result.getCity());
            response.setWeather(result.getWeather());
            response.setClothingAdvice(result.getAdvice());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("chat 处理失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ChatResponse.error(e.getMessage()));
        }
    }

    private Optional<City> resolveCity(ChatRequest request) {
        if (StringUtils.hasText(request.getCity())) {
            return cityConfig.findByName(request.getCity());
        }
        return cityConfig.extractFromText(request.getMessage());
    }

    private String buildPrompt(String message, City city, DailyWeather weather, ClothingAdvice advice) {
        return """
                用户问题：%s
                城市：%s
                今日天气：
                - 白天：%s
                - 最高温：%s℃
                - 最低温：%s℃
                穿衣建议（结构化）：
                - 概述：%s
                - 上装：%s
                - 下装：%s
                - 鞋子：%s
                - 配件：%s
                - 提醒：%s
                """.formatted(
                message,
                city != null ? nullToEmpty(city.getName()) : "",
                weather != null ? nullToEmpty(weather.getTextDay()) : "",
                weather != null ? nullToEmpty(weather.getTempMax()) : "",
                weather != null ? nullToEmpty(weather.getTempMin()) : "",
                advice != null ? nullToEmpty(advice.getOverview()) : "",
                advice != null ? nullToEmpty(advice.getTop()) : "",
                advice != null ? nullToEmpty(advice.getBottom()) : "",
                advice != null ? nullToEmpty(advice.getShoes()) : "",
                advice != null ? nullToEmpty(advice.getAccessories()) : "",
                advice != null ? nullToEmpty(advice.getReminder()) : ""
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Data
    public static class ChatRequest {
        private String message;
        private String city;
    }

    @Data
    public static class ChatResponse {
        private boolean success;
        private String errorMessage;
        private String content;
        private City city;
        private DailyWeather weather;
        private ClothingAdvice clothingAdvice;

        public static ChatResponse error(String message) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(false);
            response.setErrorMessage(message);
            return response;
        }
    }
}
