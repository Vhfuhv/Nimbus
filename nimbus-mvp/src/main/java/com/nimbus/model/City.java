package com.nimbus.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 城市信息
 *
 * @author Nimbus Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class City {

    /** 和风天气 LocationID */
    private String locationId;

    /** 城市名称 */
    private String name;

    /** 省级行政区 */
    private String adm1;

    /** 市级行政区 */
    private String adm2;

    /** 纬度 */
    private String latitude;

    /** 经度 */
    private String longitude;

    @Override
    public String toString() {
        return String.format("%s%s", adm1 != null ? adm1 : "", name);
    }
}
