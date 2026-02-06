package com.nimbus.controller;

import com.nimbus.config.CityConfig;
import com.nimbus.model.City;
import com.nimbus.model.ClothingAdvice;
import com.nimbus.model.DailyWeather;
import com.nimbus.model.WeatherResponse;
import com.nimbus.service.WeatherQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 天气查询控制器
 *
 * @author Nimbus Team
 */
@Slf4j
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherQueryService weatherQueryService;
    private final CityConfig cityConfig;

    /**
     * 查询城市今日天气及穿衣建议
     *
     * @param city 城市名称
     * @return 天气及穿衣建议
     */

    //http://localhost:14567/weather/%E5%8C%97%E4%BA%AC
    @GetMapping("/{city}")
    public ResponseEntity<WeatherWithAdviceResponse> getWeatherWithAdvice(
            @PathVariable String city) {
        log.info("收到查询请求: city={}", city);

        try {
            WeatherQueryService.WeatherResult result = weatherQueryService.queryToday(city);

            WeatherWithAdviceResponse response = new WeatherWithAdviceResponse();
            response.setSuccess(true);
            response.setCity(result.getCity());
            response.setWeather(result.getWeather());
            response.setClothingAdvice(result.getAdvice());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询天气失败: {}", e.getMessage(), e);
            WeatherWithAdviceResponse error = new WeatherWithAdviceResponse();
            error.setSuccess(false);
            error.setErrorMessage(e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 查询城市7天天气预报
     *
     * @param city 城市名称
     * @return 7天天气预报
     */
    @GetMapping("/{city}/forecast")
    public ResponseEntity<ForecastResponse> get7DayForecast(
            @PathVariable String city) {
        log.info("收到7天预报查询请求: city={}", city);

        try {
            WeatherResponse forecast = weatherQueryService.query7DayForecast(city);

            ForecastResponse response = new ForecastResponse();
            response.setSuccess(true);
            response.setDaily(forecast.getDaily());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询天气预报失败: {}", e.getMessage(), e);
            ForecastResponse error = new ForecastResponse();
            error.setSuccess(false);
            error.setErrorMessage(e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取支持的城市列表
     *
     * @return 城市列表
     */
    @GetMapping("/cities")
    public ResponseEntity<CityListResponse> getSupportedCities() {
        List<City> cities = cityConfig.getHotCities();

        CityListResponse response = new CityListResponse();
        response.setSuccess(true);
        response.setCities(cities);

        return ResponseEntity.ok(response);
    }

    // ========== 响应DTO ==========

    @lombok.Data
    public static class WeatherWithAdviceResponse {
        private boolean success;
        private String errorMessage;
        private City city;
        private DailyWeather weather;
        private ClothingAdvice clothingAdvice;
    }

    @lombok.Data
    public static class ForecastResponse {
        private boolean success;
        private String errorMessage;
        private List<DailyWeather> daily;
    }

    @lombok.Data
    public static class CityListResponse {
        private boolean success;
        private List<City> cities;
    }
}
