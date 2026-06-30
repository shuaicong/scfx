package com.scfx.enums;

public enum ParseStatusEnum {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed");

    private final String code;

    ParseStatusEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ParseStatusEnum fromCode(String code) {
        for (ParseStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("不支持的 parse status: " + code);
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
