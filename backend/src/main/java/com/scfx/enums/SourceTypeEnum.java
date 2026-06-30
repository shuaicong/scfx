package com.scfx.enums;

public enum SourceTypeEnum {
    WASDE("wasde"),
    CONAB("conab");

    private final String code;

    SourceTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static SourceTypeEnum fromCode(String code) {
        for (SourceTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("不支持的 source_type: " + code);
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
