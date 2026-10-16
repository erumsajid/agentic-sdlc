package com.agenticsdlc.shortener.dto;

import java.time.Instant;

public record ShortUrlResponse(String code, String shortUrl, String targetUrl,
                               Instant createdAt, Instant expiresAt) { }
