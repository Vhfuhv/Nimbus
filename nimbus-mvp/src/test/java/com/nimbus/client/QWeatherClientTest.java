package com.nimbus.client;

import com.nimbus.model.WeatherResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 和风天气 API 客户端测试
 *
 * @author Nimbus Team
 */
class QWeatherClientTest {

    private MockWebServer mockWebServer;
    private QWeatherClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        client = new QWeatherClient(webClient, "test-api-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testGet7DayForecast_Success() {
        // 模拟和风 API 响应
        String mockResponse = """
            {
                "code": "200",
                "updateTime": "2025-02-06T10:00+08:00",
                "fxLink": "https://www.qweather.com",
                "daily": [
                    {
                        "fxDate": "2025-02-06",
                        "tempMax": "10",
                        "tempMin": "2",
                        "textDay": "晴",
                        "textNight": "多云",
                        "windDirDay": "北风",
                        "windScaleDay": "3-4级",
                        "humidity": "45"
                    }
                ]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(mockResponse));

        WeatherResponse response = client.get7DayForecast("101010100");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("200", response.getCode());
        assertNotNull(response.getDaily());
        assertEquals(1, response.getDaily().size());

        var today = response.getToday();
        assertNotNull(today);
        assertEquals("10", today.getTempMax());
        assertEquals("2", today.getTempMin());
        assertEquals("晴", today.getTextDay());
    }

    @Test
    void testGet7DayForecast_ApiError() {
        String errorResponse = """
            {
                "code": "401",
                "daily": []
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(errorResponse));

        WeatherResponse response = client.get7DayForecast("101010100");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("401", response.getCode());
    }
}
