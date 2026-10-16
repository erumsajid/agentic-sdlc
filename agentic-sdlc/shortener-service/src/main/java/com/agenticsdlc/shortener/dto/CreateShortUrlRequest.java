package com.agenticsdlc.shortener.dto;

import java.time.Instant;

public record CreateShortUrlRequest(String url, String customAlias, Instant expiresAt) { }
