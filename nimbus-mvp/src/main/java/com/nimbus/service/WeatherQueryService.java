package com.nimbus.service;

import com.nimbus.client.QWeatherClient;
import com.nimbus.config.CityConfig;
import com.nimbus.model.City;
import com.nimbus.model.ClothingAdvice;
import com.nimbus.model.DailyWeather;
import com.nimbus.model.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 天气查询服务
 *
 * @author Nimbus Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private final QWeatherClient weatherClient;
    private final CityConfig cityConfig;
    private final ClothingAdviceService clothingAdviceService;

    /**
     * 查询城市今日天气及穿衣建议
     *
     * @param cityName 城市名称
     * @return 天气及穿衣建议
     */
    public WeatherResult queryToday(String cityName) {
        log.info("查询城市今日天气: {}", cityName);

        // 1. 查找城市
        City city = cityConfig.findByName(cityName)
                .orElseThrow(() -> new RuntimeException("未找到城市: " + cityName));

        return queryToday(city);
    }

    public WeatherResult queryToday(City city) {
        // 2. 查询天气
        WeatherResponse response = weatherClient.get7DayForecast(city.getLocationId());

        if (!response.isSuccess() || response.getToday() == null) {
            throw new RuntimeException("获取天气数据失败");
        }

        DailyWeather today = response.getToday();

        // 3. 生成穿衣建议
        ClothingAdvice advice = clothingAdviceService.generateAdvice(today);

        return WeatherResult.builder()
                .city(city)
                .weather(today)
                .advice(advice)
                .build();
    }

    /**
     * 查询城市7天天气预报
     *
     * @param cityName 城市名称
     * @return 7天天气预报
     */
    public WeatherResponse query7DayForecast(String cityName) {
        log.info("查询城市7天预报: {}", cityName);

        City city = cityConfig.findByName(cityName)
                .orElseThrow(() -> new RuntimeException("未找到城市: " + cityName));

        return query7DayForecast(city);
    }

    public WeatherResponse query7DayForecast(City city) {
        return weatherClient.get7DayForecast(city.getLocationId());
    }

    /**
     * 天气查询结果
     */
    @lombok.Data
    @lombok.Builder
    public static class WeatherResult {
        private City city;
        private DailyWeather weather;
        private ClothingAdvice advice;
    }
}
