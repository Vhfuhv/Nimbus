package com.nimbus.agentai.client;

import com.nimbus.agentai.model.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class QWeatherClient {

    private final WebClient webClient;
    private final String apiKey;

    public QWeatherClient(WebClient weatherWebClient,
                          @Value("${nimbus.weather.api-key}") String apiKey) {
        this.webClient = weatherWebClient;
        this.apiKey = apiKey;
    }

    public WeatherResponse get7DayForecast(String locationId) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v7/weather/7d")
                            .queryParam("location", locationId)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .doOnNext(response -> log.debug("收到响应: code={}", response != null ? response.getCode() : "null"))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("和风 API 调用失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("天气服务暂时不可用", e);
        } catch (Exception e) {
            log.error("查询天气异常", e);
            throw new RuntimeException("查询天气失败", e);
        }
    }
}

