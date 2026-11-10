package com.yunpei.proxy.service.externalai.workorder.v3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * V3 字段袋（规则 + LLM 字段合并结果）。
 *
 * @author yunpei
 */
@Data
public class ExtractedFields {

    private String projectName;
    private String productName;
    private String brandName;
    private String landingUrl;
    private String eventName;
    private String customEventType;
    private List<String> countries = new ArrayList<>();
    private Integer ageMin;
    private Integer ageMax;
    private String gender;
    private String audience;
    private Double dailyBudget;
    private String budgetType = "campaign";
    private boolean bidWantsCap;
    private Double bidAmount;
    private String creativeType = "image";
    private List<String> devicePlatforms = new ArrayList<>();
    private String placementMode;
    private List<String> publisherPlatforms = new ArrayList<>();
    private String startType = "immediate";
    private String startTime;
    private String endTime;
    private Integer runStartTime;
    private Integer runStopTime;
    private String objectStoreUrl;
    private String mobileAppStore;
    private Integer attributionClickDays;
    private Integer attributionViewDays;
    private List<String> specialAdCategories = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();
    private String targetingDescription = "";
    private Map<String, String> sources = new LinkedHashMap<>();
}
