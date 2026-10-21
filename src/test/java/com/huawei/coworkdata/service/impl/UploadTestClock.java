package com.huawei.coworkdata.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** H2 lacks statement_timestamp(); do not replace the production write SQL. */
public final class UploadTestClock {
    private static final ThreadLocal<OffsetDateTime> FIXED = new ThreadLocal<>();

    private UploadTestClock() { }

    public static OffsetDateTime now() {
        OffsetDateTime fixed = FIXED.get();
        return fixed == null ? OffsetDateTime.now(ZoneOffset.UTC) : fixed;
    }

    static void set(String instant) {
        FIXED.set(OffsetDateTime.parse(instant));
    }

    static void clear() {
        FIXED.remove();
    }
}
