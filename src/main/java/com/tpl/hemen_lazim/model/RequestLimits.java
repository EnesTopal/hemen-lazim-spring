package com.tpl.hemen_lazim.model;

/**
 * Talep sınırlarının tek kaynağı. Uygulamadaki model/RequestLimits.kt ile aynı tutulmalı:
 * kaydırıcılar bu aralığın dışına çıkamıyor, sunucu da dışını reddediyor.
 */
public final class RequestLimits {

    public static final int MIN_RADIUS_M = 100;
    public static final int MAX_RADIUS_M = 10_000;
    public static final int DEFAULT_RADIUS_M = 1500;

    public static final int MIN_DURATION_MIN = 15;
    public static final int MAX_DURATION_MIN = 24 * 60;
    public static final int DEFAULT_DURATION_MIN = 60;

    private RequestLimits() {}
}
