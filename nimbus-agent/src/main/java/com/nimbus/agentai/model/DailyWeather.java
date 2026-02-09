package com.nimbus.agentai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DailyWeather {
    @JsonProperty("fxDate")
    private String fxDate;
    @JsonProperty("sunrise")
    private String sunrise;
    @JsonProperty("sunset")
    private String sunset;
    @JsonProperty("moonrise")
    private String moonrise;
    @JsonProperty("moonset")
    private String moonset;
    @JsonProperty("tempMax")
    private String tempMax;
    @JsonProperty("tempMin")
    private String tempMin;
    @JsonProperty("iconDay")
    private String iconDay;
    @JsonProperty("textDay")
    private String textDay;
    @JsonProperty("iconNight")
    private String iconNight;
    @JsonProperty("textNight")
    private String textNight;
    @JsonProperty("windDirDay")
    private String windDirDay;
    @JsonProperty("wind360Day")
    private String wind360Day;
    @JsonProperty("windScaleDay")
    private String windScaleDay;
    @JsonProperty("windSpeedDay")
    private String windSpeedDay;
    @JsonProperty("windDirNight")
    private String windDirNight;
    @JsonProperty("wind360Night")
    private String wind360Night;
    @JsonProperty("windScaleNight")
    private String windScaleNight;
    @JsonProperty("windSpeedNight")
    private String windSpeedNight;
    @JsonProperty("precip")
    private String precip;
    @JsonProperty("uvIndex")
    private String uvIndex;
    @JsonProperty("humidity")
    private String humidity;
    @JsonProperty("pressure")
    private String pressure;
    @JsonProperty("vis")
    private String vis;
    @JsonProperty("cloud")
    private String cloud;

    public int getTempMaxValue() {
        try {
            return Integer.parseInt(tempMax);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int getTempMinValue() {
        try {
            return Integer.parseInt(tempMin);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int getAverageTemp() {
        return (getTempMaxValue() + getTempMinValue()) / 2;
    }

    public boolean isRainy() {
        if (textDay == null) {
            return false;
        }
        String weather = textDay.toLowerCase();
        return weather.contains("雨") || weather.contains("雪");
    }
}

