package com.nimbus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 单日天气数据
 *
 * @author Nimbus Team
 */
@Data
public class DailyWeather {

    /** 预报日期 */
    @JsonProperty("fxDate")
    private String fxDate;

    /** 日出时间 */
    @JsonProperty("sunrise")
    private String sunrise;

    /** 日落时间 */
    @JsonProperty("sunset")
    private String sunset;

    /** 月升时间 */
    @JsonProperty("moonrise")
    private String moonrise;

    /** 月落时间 */
    @JsonProperty("moonset")
    private String moonset;

    /** 最高温度 */
    @JsonProperty("tempMax")
    private String tempMax;

    /** 最低温度 */
    @JsonProperty("tempMin")
    private String tempMin;

    /** 白天天气图标代码 */
    @JsonProperty("iconDay")
    private String iconDay;

    /** 白天天气状况描述 */
    @JsonProperty("textDay")
    private String textDay;

    /** 夜间天气图标代码 */
    @JsonProperty("iconNight")
    private String iconNight;

    /** 夜间天气状况描述 */
    @JsonProperty("textNight")
    private String textNight;

    /** 白天风向 */
    @JsonProperty("windDirDay")
    private String windDirDay;

    /** 白天风向360角度 */
    @JsonProperty("wind360Day")
    private String wind360Day;

    /** 白天风力等级 */
    @JsonProperty("windScaleDay")
    private String windScaleDay;

    /** 白天风速，公里/小时 */
    @JsonProperty("windSpeedDay")
    private String windSpeedDay;

    /** 夜间风向 */
    @JsonProperty("windDirNight")
    private String windDirNight;

    /** 夜间风向360角度 */
    @JsonProperty("wind360Night")
    private String wind360Night;

    /** 夜间风力等级 */
    @JsonProperty("windScaleNight")
    private String windScaleNight;

    /** 夜间风速，公里/小时 */
    @JsonProperty("windSpeedNight")
    private String windSpeedNight;

    /** 总降水量，毫米 */
    @JsonProperty("precip")
    private String precip;

    /** 紫外线强度指数 */
    @JsonProperty("uvIndex")
    private String uvIndex;

    /** 相对湿度，百分比数值 */
    @JsonProperty("humidity")
    private String humidity;

    /** 大气压强，百帕 */
    @JsonProperty("pressure")
    private String pressure;

    /** 能见度，公里 */
    @JsonProperty("vis")
    private String vis;

    /** 云量，百分比数值 */
    @JsonProperty("cloud")
    private String cloud;

    /**
     * 获取最高温度数值
     */
    public int getTempMaxValue() {
        try {
            return Integer.parseInt(tempMax);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取最低温度数值
     */
    public int getTempMinValue() {
        try {
            return Integer.parseInt(tempMin);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取平均温度
     */
    public int getAverageTemp() {
        return (getTempMaxValue() + getTempMinValue()) / 2;
    }

    /**
     * 是否下雨
     */
    public boolean isRainy() {
        if (textDay == null) return false;
        String weather = textDay.toLowerCase();
        return weather.contains("雨") || weather.contains("雪");
    }
}
