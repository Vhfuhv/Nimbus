package com.nimbus.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 穿衣建议
 *
 * @author Nimbus Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClothingAdvice {

    /** 建议概述 */
    private String overview;

    /** 上装建议 */
    private String top;

    /** 下装建议 */
    private String bottom;

    /** 鞋子建议 */
    private String shoes;

    /** 配件建议 */
    private String accessories;

    /** 额外提醒 */
    private String reminder;

    /**
     * 创建简化的穿衣建议
     */
    public static ClothingAdvice simple(String overview, String top, String bottom,
                                        String shoes, String accessories) {
        return ClothingAdvice.builder()
                .overview(overview)
                .top(top)
                .bottom(bottom)
                .shoes(shoes)
                .accessories(accessories)
                .build();
    }
}
