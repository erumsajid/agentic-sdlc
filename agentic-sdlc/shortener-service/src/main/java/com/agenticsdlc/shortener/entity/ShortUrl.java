package com.agenticsdlc.shortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "short_url")
public class ShortUrl {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "target_url", nullable = false, columnDefinition = "text")
    private String targetUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    protected ShortUrl() { }

    public ShortUrl(UUID id, String code, String targetUrl, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.code = code;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.clickCount = 0;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getTargetUrl() { return targetUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getClickCount() { return clickCount; }
    public boolean isExpiredAt(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
    public void recordClick() { clickCount++; }
}
