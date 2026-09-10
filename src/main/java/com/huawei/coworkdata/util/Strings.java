package com.huawei.coworkdata.util;

/**
 * NOT NULL 文本列兜底：显式 INSERT null 时 DB DEFAULT 不生效，必须在应用层把 null 收成空串。
 */
public final class Strings {

    private Strings() {
    }

    public static String nz(String value) {
        return value == null ? "" : value;
    }

    public static String nz(String value, String defaultVal) {
        if (value == null || value.trim().isEmpty()) {
            return defaultVal != null ? defaultVal : "";
        }
        return value;
    }

    public static String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
