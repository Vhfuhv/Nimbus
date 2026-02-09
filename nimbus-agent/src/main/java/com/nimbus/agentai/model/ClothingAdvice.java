package com.nimbus.agentai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClothingAdvice {
    private String overview;
    private String top;
    private String bottom;
    private String shoes;
    private String accessories;
    private String reminder;

    public static ClothingAdvice simple(String overview, String top, String bottom, String shoes, String accessories) {
        return ClothingAdvice.builder()
                .overview(overview)
                .top(top)
                .bottom(bottom)
                .shoes(shoes)
                .accessories(accessories)
                .build();
    }
}

