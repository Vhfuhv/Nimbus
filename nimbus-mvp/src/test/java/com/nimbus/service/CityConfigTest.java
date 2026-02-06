package com.nimbus.service;

import com.nimbus.config.CityConfig;
import com.nimbus.model.City;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 城市配置测试
 *
 * @author Nimbus Team
 */
class CityConfigTest {

    private CityConfig cityConfig;

    @BeforeEach
    void setUp() {
        cityConfig = new CityConfig();
        cityConfig.init();
    }

    @Test
    void testInit_LoadedCities() {
        List<City> cities = cityConfig.getAllCities();
        assertTrue(cities.size() > 3000);
        assertEquals(10, cityConfig.getHotCities().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"北京", "上海", "广州", "深圳", "杭州", "成都", "西安", "武汉", "南京", "厦门"})
    void testFindByName_ExactMatch(String cityName) {
        Optional<City> result = cityConfig.findByName(cityName);

        assertTrue(result.isPresent());
        assertEquals(cityName, result.get().getName());
    }

    @Test
    void testFindByName_WithSuffix() {
        Optional<City> result = cityConfig.findByName("北京市");

        assertTrue(result.isPresent());
        assertEquals("北京", result.get().getName());
    }

    @Test
    void testFindByName_NotFound() {
        Optional<City> result = cityConfig.findByName("火星");

        assertFalse(result.isPresent());
    }

    @Test
    void testFindByName_FuzzyMatch() {
        Optional<City> result = cityConfig.findByName("广州深圳");

        // 应该返回广州或深圳中的一个
        assertTrue(result.isPresent());
    }

    @Test
    void testExtractFromText() {
        Optional<City> result = cityConfig.extractFromText("帮我查一下北京今天穿什么");
        assertTrue(result.isPresent());
        assertEquals("北京", result.get().getName());
    }

    @Test
    void testCity_HasLocationId() {
        Optional<City> beijing = cityConfig.findByName("北京");

        assertTrue(beijing.isPresent());
        assertNotNull(beijing.get().getLocationId());
        assertEquals(9, beijing.get().getLocationId().length()); // 和风ID是9位
    }
}
