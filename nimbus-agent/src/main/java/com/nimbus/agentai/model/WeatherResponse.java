package com.nimbus.agentai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WeatherResponse {
    @JsonProperty("code")
    private String code;
    @JsonProperty("updateTime")
    private String updateTime;
    @JsonProperty("fxLink")
    private String fxLink;
    @JsonProperty("daily")
    private List<DailyWeather> daily;
    @JsonProperty("refer")
    private Refer refer;

    public boolean isSuccess() {
        return "200".equals(code);
    }

    public DailyWeather getToday() {
        if (daily == null || daily.isEmpty()) {
            return null;
        }
        return daily.get(0);
    }

    @Data
    public static class Refer {
        @JsonProperty("sources")
        private List<String> sources;
        @JsonProperty("license")
        private List<String> license;
    }
}

