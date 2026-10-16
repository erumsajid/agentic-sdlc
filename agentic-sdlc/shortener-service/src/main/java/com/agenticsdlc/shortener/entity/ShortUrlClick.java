package com.agenticsdlc.shortener.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "short_url_click")
public class ShortUrlClick {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    private Instant accessedAt;

    private String referrerHost;

    protected ShortUrlClick() { }

    public ShortUrlClick(ShortUrl shortUrl, Instant accessedAt, String referrerHost) {
        this.shortUrl = shortUrl;
        this.accessedAt = accessedAt;
        this.referrerHost = referrerHost;
    }
}
