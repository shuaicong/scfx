package com.scfx.enums;

public enum AttributeEnum {
    PRODUCTION("PRODUCTION", "产量"),
    IMPORTS("IMPORTS", "进口"),
    EXPORTS("EXPORTS", "出口"),
    ENDING_STOCK("ENDING_STOCK", "期末库存"),
    STOCK_USE_RATIO("STOCK_USE_RATIO", "库存消费比");

    private final String code;
    private final String label;

    AttributeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AttributeEnum fromCode(String code) {
        for (AttributeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("不支持的 attribute: " + code);
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
