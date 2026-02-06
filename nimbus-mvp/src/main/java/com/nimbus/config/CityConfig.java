package com.nimbus.config;

import com.nimbus.model.City;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 城市配置 - 从 CSV 城市库加载（和风 LocationID）
 */
@Slf4j
@Component
public class CityConfig implements ResourceLoaderAware {

    private static final List<String> DEFAULT_HOT_CITIES = List.of(
            "北京", "上海", "广州", "深圳", "西安", "南京", "杭州", "武汉", "厦门", "成都"
    );

    @Getter
    private final Map<String, City> cities = new HashMap<>();

    private List<String> cityNamesByLengthDesc = List.of();

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    private String csvLocation = "classpath:assets/China-City-List-latest.csv";

    private List<String> hotCityNames = DEFAULT_HOT_CITIES;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        if (resourceLoader != null) {
            this.resourceLoader = resourceLoader;
        }
    }

    @Value("${nimbus.city.csv-location:classpath:assets/China-City-List-latest.csv}")
    public void setCsvLocation(String csvLocation) {
        if (csvLocation != null && !csvLocation.isBlank()) {
            this.csvLocation = csvLocation.trim();
        }
    }

    @Value("${nimbus.city.hot:}")
    public void setHotCityNames(String hot) {
        if (hot == null || hot.isBlank()) {
            return;
        }
        List<String> parsed = Arrays.stream(hot.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (!parsed.isEmpty()) {
            this.hotCityNames = parsed;
        }
    }

    @PostConstruct
    public void init() {
        loadFromCsv();
        cityNamesByLengthDesc = cities.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        log.info("已加载 {} 个城市（去重后）", cities.size());
    }

    private void loadFromCsv() {
        Resource resource = resourceLoader.getResource(csvLocation);
        if (!resource.exists()) {
            Resource fallback = resourceLoader.getResource("file:./assets/China-City-List-latest.csv");
            if (fallback.exists()) {
                resource = fallback;
            }
        }
        if (!resource.exists()) {
            Resource fallback = resourceLoader.getResource("file:./nimbus-mvp/assets/China-City-List-latest.csv");
            if (fallback.exists()) {
                resource = fallback;
            }
        }

        if (!resource.exists()) {
            throw new IllegalStateException("未找到城市库 CSV: " + csvLocation);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String versionLine = reader.readLine(); // e.g. China-City-List v...
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalStateException("城市库 CSV 为空: " + csvLocation);
            }

            Map<String, Integer> headerIndex = indexHeaders(headerLine);
            int idIdx = requireIndex(headerIndex, "Location_ID");
            int nameZhIdx = requireIndex(headerIndex, "Location_Name_ZH");
            int adm1ZhIdx = requireIndex(headerIndex, "Adm1_Name_ZH");
            int adm2ZhIdx = requireIndex(headerIndex, "Adm2_Name_ZH");
            int latIdx = headerIndex.getOrDefault("Latitude", -1);
            int lonIdx = headerIndex.getOrDefault("Longitude", -1);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = splitCsvLine(line);
                if (cols.length <= Math.max(Math.max(idIdx, nameZhIdx), Math.max(adm2ZhIdx, adm1ZhIdx))) {
                    continue;
                }

                String locationId = cols[idIdx].trim();
                String nameZh = cols[nameZhIdx].trim();
                if (locationId.isEmpty() || nameZh.isEmpty()) {
                    continue;
                }

                City city = City.builder()
                        .locationId(locationId)
                        .name(nameZh)
                        .adm1(safeGet(cols, adm1ZhIdx))
                        .adm2(safeGet(cols, adm2ZhIdx))
                        .latitude(latIdx >= 0 ? safeGet(cols, latIdx) : null)
                        .longitude(lonIdx >= 0 ? safeGet(cols, lonIdx) : null)
                        .build();

                upsertPreferred(city);
            }

            if (versionLine != null && !versionLine.isBlank()) {
                log.debug("城市库版本: {}", versionLine);
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载城市库失败: " + csvLocation, e);
        }
    }

    private static Map<String, Integer> indexHeaders(String headerLine) {
        String[] headers = splitCsvLine(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.put(headers[i].trim(), i);
        }
        return index;
    }

    private static int requireIndex(Map<String, Integer> headerIndex, String headerName) {
        Integer idx = headerIndex.get(headerName);
        if (idx == null) {
            throw new IllegalStateException("城市库 CSV 缺少列: " + headerName);
        }
        return idx;
    }

    private void upsertPreferred(City city) {
        City existing = cities.get(city.getName());
        if (existing == null) {
            cities.put(city.getName(), city);
            return;
        }

        boolean existingPreferred = isCityLevel(existing);
        boolean currentPreferred = isCityLevel(city);
        if (!existingPreferred && currentPreferred) {
            cities.put(city.getName(), city);
        }
    }

    private static boolean isCityLevel(City city) {
        String name = normalizeName(city.getName());
        String adm2 = normalizeName(city.getAdm2());
        return name != null && adm2 != null && name.equals(adm2);
    }

    /**
     * 根据城市名称查找（支持去掉常见后缀）
     */
    public Optional<City> findByName(String name) {
        String normalized = normalizeName(name);
        if (normalized == null || normalized.isEmpty()) {
            return Optional.empty();
        }

        City exact = cities.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }

        return cities.values().stream()
                .filter(c -> c.getName().contains(normalized) || normalized.contains(c.getName()))
                .findFirst();
    }

    /**
     * 从用户对话中提取城市（按最长匹配）
     */
    public Optional<City> extractFromText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String content = text.trim();
        for (String name : cityNamesByLengthDesc) {
            if (name.length() < 2) {
                continue;
            }
            if (content.contains(name)) {
                City city = cities.get(name);
                if (city != null) {
                    return Optional.of(city);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 获取“热门城市”列表（用于 UI/示例）
     */
    public List<City> getHotCities() {
        List<City> result = new ArrayList<>();
        for (String name : hotCityNames) {
            findByName(name).ifPresent(result::add);
        }
        return result;
    }

    public List<City> getAllCities() {
        return List.copyOf(cities.values());
    }

    private static String safeGet(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) {
            return null;
        }
        String v = cols[idx].trim();
        return v.isEmpty() ? null : v;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String[] suffixes = {"市", "区", "县", "省", "自治州", "地区", "盟", "旗"};
        for (String suffix : suffixes) {
            if (trimmed.endsWith(suffix) && trimmed.length() > suffix.length()) {
                trimmed = trimmed.substring(0, trimmed.length() - suffix.length());
                break;
            }
        }
        return trimmed;
    }

    private static String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }
}
