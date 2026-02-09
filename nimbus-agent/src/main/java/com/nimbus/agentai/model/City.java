package com.nimbus.agentai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class City {
    private String locationId;
    private String name;
    private String adm1;
    private String adm2;
    private String latitude;
    private String longitude;

    @Override
    public String toString() {
        return String.format("%s%s", adm1 != null ? adm1 : "", name);
    }
}

