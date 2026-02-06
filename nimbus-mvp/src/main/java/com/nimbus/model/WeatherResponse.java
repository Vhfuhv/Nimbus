package com.nimbus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 和风天气 API 7天预报响应
 *
 * @author Nimbus Team
 */
@Data
public class WeatherResponse {

    /** API状态码 */
    @JsonProperty("code")
    private String code;

    /** 当前API的最近更新时间 */
    @JsonProperty("updateTime")
    private String updateTime;

    /** 当前数据的响应式页面，便于嵌入网站或应用 */
    @JsonProperty("fxLink")
    private String fxLink;

    /** 7天天气预报数据 */
    @JsonProperty("daily")
    private List<DailyWeather> daily;

    /** 数据来源 */
    @JsonProperty("refer")
    private Refer refer;

    /**
     * 请求是否成功
     */
    public boolean isSuccess() {
        return "200".equals(code);
    }

    /**
     * 获取今天的天气
     */
    public DailyWeather getToday() {
        if (daily == null || daily.isEmpty()) {
            return null;
        }
        return daily.get(0);
    }

    /**
     * 获取明天的天气
     */
    public DailyWeather getTomorrow() {
        if (daily == null || daily.size() < 2) {
            return null;
        }
        return daily.get(1);
    }

    @Data
    public static class Refer {
        /** 原始数据来源 */
        @JsonProperty("sources")
        private List<String> sources;

        /** 数据许可或版权声明 */
        @JsonProperty("license")
        private List<String> license;
    }
}
