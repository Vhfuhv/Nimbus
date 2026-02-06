package com.nimbus.service;

import com.nimbus.model.ClothingAdvice;
import com.nimbus.model.DailyWeather;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 穿衣建议服务测试
 *
 * @author Nimbus Team
 */
class ClothingAdviceServiceTest {

    private ClothingAdviceService service;

    @BeforeEach
    void setUp() {
        service = new ClothingAdviceService();
    }

    @Test
    void testGenerateAdvice_NullWeather() {
        ClothingAdvice advice = service.generateAdvice(null);

        assertNotNull(advice);
        assertEquals("暂无天气数据", advice.getOverview());
    }

    @ParameterizedTest
    @CsvSource({
            "-15, 极寒",
            "-5, 寒冷",
            "0, 较冷",
            "8, 凉爽",
            "12, 舒适",
            "18, 温暖",
            "22, 温暖",
            "28, 较热",
            "35, 炎热"
    })
    void testGenerateAdvice_ByTemperature(int temp, String keyword) {
        DailyWeather weather = createWeather(temp, "晴");

        ClothingAdvice advice = service.generateAdvice(weather);

        assertNotNull(advice);
        assertNotNull(advice.getOverview());
        assertNotNull(advice.getTop());
        assertNotNull(advice.getBottom());
        assertNotNull(advice.getShoes());
    }

    @Test
    void testGenerateAdvice_RainyDay() {
        DailyWeather weather = createWeather(20, "小雨");

        ClothingAdvice advice = service.generateAdvice(weather);

        assertNotNull(advice);
        assertTrue(advice.getReminder().contains("雨"));
        assertEquals("雨伞", advice.getAccessories());
        assertEquals("防水鞋/雨鞋", advice.getShoes());
    }

    @Test
    void testGenerateAdvice_SunnyDay() {
        DailyWeather weather = createWeather(25, "晴");

        ClothingAdvice advice = service.generateAdvice(weather);

        assertNotNull(advice);
        assertTrue(advice.getAccessories().contains("遮阳") || advice.getAccessories().contains("太阳"));
    }

    private DailyWeather createWeather(int temp, String weatherText) {
        DailyWeather weather = new DailyWeather();
        weather.setTempMax(String.valueOf(temp + 5));
        weather.setTempMin(String.valueOf(temp - 5));
        weather.setTextDay(weatherText);
        return weather;
    }
}
