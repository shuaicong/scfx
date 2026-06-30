package com.scfx.enums;

public enum CommodityEnum {
    CORN("CORN", "玉米"),
    WHEAT("WHEAT", "小麦"),
    SOYBEANS("SOYBEANS", "大豆"),
    RICE("RICE", "稻米"),
    COTTON("COTTON", "棉花");

    private final String code;
    private final String label;

    CommodityEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CommodityEnum fromCode(String code) {
        for (CommodityEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("不支持的 commodity: " + code);
    }

    public static boolean isValid(String code) {
        try {
            fromCode(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
