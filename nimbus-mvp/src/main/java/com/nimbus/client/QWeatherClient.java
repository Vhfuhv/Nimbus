package com.nimbus.client;

import com.nimbus.model.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 和风天气 API 客户端
 *
 * @author Nimbus Team
 */
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

    /**
     * 获取 7 天天气预报
     *
     * @param locationId 城市 LocationID
     * @return 天气预报数据
     */
    public WeatherResponse get7DayForecast(String locationId) {
        log.debug("查询城市 {} 的7天天气预报", locationId);

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v7/weather/7d")
                            .queryParam("location", locationId)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .doOnNext(response -> log.debug("收到响应: code={}", response.getCode()))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("和风 API 调用失败: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("天气服务暂时不可用", e);
        } catch (Exception e) {
            log.error("查询天气异常", e);
            throw new RuntimeException("查询天气失败", e);
        }
    }

    /**
     * 异步获取 7 天天气预报
     *
     * @param locationId 城市 LocationID
     * @return Mono<天气预报数据>
     */
    public Mono<WeatherResponse> get7DayForecastAsync(String locationId) {
        log.debug("异步查询城市 {} 的7天天气预报", locationId);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v7/weather/7d")
                        .queryParam("location", locationId)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(WeatherResponse.class)
                .doOnNext(response -> log.debug("收到响应: code={}", response.getCode()))
                .doOnError(e -> log.error("查询天气异常", e));
    }
}
