package com.nimbus.service;

import com.nimbus.model.ClothingAdvice;
import com.nimbus.model.DailyWeather;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 穿衣建议服务 - 基于规则的简单实现
 *
 * @author Nimbus Team
 */
@Slf4j
@Service
public class ClothingAdviceService {

    /**
     * 根据天气生成穿衣建议
     *
     * @param weather 天气数据
     * @return 穿衣建议
     */
    public ClothingAdvice generateAdvice(DailyWeather weather) {
        if (weather == null) {
            return ClothingAdvice.builder()
                    .overview("暂无天气数据")
                    .build();
        }

        int avgTemp = weather.getAverageTemp();
        boolean isRainy = weather.isRainy();
        String weatherText = weather.getTextDay();

        log.debug("生成穿衣建议: 平均温度={}, 天气={}", avgTemp, weatherText);

        // 基于温度范围生成建议
        ClothingAdvice advice = generateByTemperature(avgTemp);

        // 根据天气状况调整
        if (isRainy) {
            advice.setReminder("今天有" + weatherText + "，记得带伞 ☔");
            advice.setShoes("防水鞋/雨鞋");
            advice.setAccessories("雨伞");
        } else if (weatherText != null && weatherText.contains("晴")) {
            advice.setAccessories("太阳镜/遮阳帽");
        }

        return advice;
    }

    /**
     * 基于温度生成基础穿衣建议
     */
    private ClothingAdvice generateByTemperature(int temp) {
        if (temp < -10) {
            return ClothingAdvice.simple(
                    "极寒天气，注意保暖！",
                    "厚羽绒服 + 保暖内衣 + 毛衣",
                    "加绒裤/羽绒裤",
                    "雪地靴/棉靴",
                    "围巾、手套、帽子、口罩"
            );
        } else if (temp < 0) {
            return ClothingAdvice.simple(
                    "天气寒冷，全副武装",
                    "羽绒服 + 毛衣",
                    "厚裤子/加绒裤",
                    "保暖鞋/靴子",
                    "围巾、手套、帽子"
            );
        } else if (temp < 5) {
            return ClothingAdvice.simple(
                    "天气较冷，注意保暖",
                    "羽绒服/厚棉服",
                    "长裤/厚裤子",
                    "运动鞋/休闲鞋",
                    "围巾、手套"
            );
        } else if (temp < 10) {
            return ClothingAdvice.simple(
                    "天气凉爽",
                    "大衣/棉服/夹克 + 薄毛衣",
                    "长裤",
                    "运动鞋/休闲鞋",
                    "薄围巾"
            );
        } else if (temp < 15) {
            return ClothingAdvice.simple(
                    "舒适温度",
                    "外套/风衣/卫衣",
                    "长裤/牛仔裤",
                    "运动鞋/休闲鞋",
                    "根据需要携带薄外套"
            );
        } else if (temp < 20) {
            return ClothingAdvice.simple(
                    "温暖舒适",
                    "薄外套/长袖衬衫/T恤",
                    "长裤/休闲裤",
                    "运动鞋/帆布鞋",
                    ""
            );
        } else if (temp < 25) {
            return ClothingAdvice.simple(
                    "天气温暖",
                    "T恤/衬衫",
                    "长裤/薄裤",
                    "运动鞋/休闲鞋",
                    ""
            );
        } else if (temp < 30) {
            return ClothingAdvice.simple(
                    "天气较热",
                    "短袖/薄衬衫",
                    "短裤/薄长裤",
                    "凉鞋/透气鞋",
                    "遮阳帽"
            );
        } else {
            return ClothingAdvice.simple(
                    "天气炎热，注意防暑！",
                    "短袖/背心/薄衣物",
                    "短裤/裙装",
                    "凉鞋/拖鞋",
                    "遮阳帽、太阳镜、防晒霜"
            );
        }
    }
}
