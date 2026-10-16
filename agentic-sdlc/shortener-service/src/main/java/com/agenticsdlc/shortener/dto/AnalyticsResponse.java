package com.agenticsdlc.shortener.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsResponse(String code, long totalClicks, Instant createdAt,
                                Instant expiresAt, Map<String, Long> clicksByReferrer,
                                List<DailyClicks> dailyClicks) {
    public record DailyClicks(Instant date, long clicks) { }
}
