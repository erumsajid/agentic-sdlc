package com.agenticsdlc.shortener.service;

import com.agenticsdlc.shortener.dto.AnalyticsResponse;
import com.agenticsdlc.shortener.dto.CreateShortUrlRequest;
import com.agenticsdlc.shortener.dto.ShortUrlResponse;
import com.agenticsdlc.shortener.entity.ShortUrl;
import com.agenticsdlc.shortener.entity.ShortUrlClick;
import com.agenticsdlc.shortener.repository.ShortUrlClickRepository;
import com.agenticsdlc.shortener.repository.ShortUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShortUrlService {
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ShortUrlRepository urls;
    private final ShortUrlClickRepository clicks;
    private final String publicBaseUrl;

    public ShortUrlService(ShortUrlRepository urls, ShortUrlClickRepository clicks,
                           @Value("${shortener.public-base-url}") String publicBaseUrl) {
        this.urls = urls;
        this.clicks = clicks;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        String target = validateTarget(request.url());
        Instant now = Instant.now();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }

        String code = request.customAlias() == null || request.customAlias().isBlank()
                ? generateCode() : validateAlias(request.customAlias());
        if (urls.existsByCode(code)) {
            throw new AliasConflictException("The requested short code is already in use");
        }
        ShortUrl saved;
        try {
            saved = urls.saveAndFlush(new ShortUrl(UUID.randomUUID(), code, target, now, request.expiresAt()));
        } catch (DataIntegrityViolationException e) {
            throw new AliasConflictException("The requested short code is already in use");
        }
        return toResponse(saved);
    }

    @Transactional
    public String resolveAndRecord(String code, String referrer) {
        ShortUrl url = urls.findByCode(code).orElseThrow(() -> new ShortUrlNotFoundException("Short URL not found"));
        if (url.isExpiredAt(Instant.now())) {
            throw new ShortUrlNotFoundException("Short URL has expired");
        }
        urls.incrementClickCount(url.getId());
        clicks.save(new ShortUrlClick(url, Instant.now(), referrerHost(referrer)));
        return url.getTargetUrl();
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(String code) {
        ShortUrl url = urls.findByCode(code).orElseThrow(() -> new ShortUrlNotFoundException("Short URL not found"));
        Map<String, Long> byReferrer = new LinkedHashMap<>();
        for (Object[] row : clicks.countByReferrer(url.getId())) {
            byReferrer.put((String) row[0], ((Number) row[1]).longValue());
        }
        List<AnalyticsResponse.DailyClicks> daily = clicks.countDailySince(url.getId(),
                        url.getCreatedAt().minus(1, ChronoUnit.DAYS)).stream()
                .map(row -> new AnalyticsResponse.DailyClicks(toInstant(row[0]), ((Number) row[1]).longValue()))
                .toList();
        return new AnalyticsResponse(url.getCode(), clicks.countByShortUrlId(url.getId()),
                url.getCreatedAt(), url.getExpiresAt(), byReferrer, daily);
    }

    private ShortUrlResponse toResponse(ShortUrl url) {
        return new ShortUrlResponse(url.getCode(), publicBaseUrl + "/" + url.getCode(),
                url.getTargetUrl(), url.getCreatedAt(), url.getExpiresAt());
    }

    private String generateCode() {
        String code;
        do {
            StringBuilder result = new StringBuilder(8);
            for (int i = 0; i < 8; i++) result.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            code = result.toString();
        } while (urls.existsByCode(code));
        return code;
    }

    private String validateAlias(String alias) {
        if (!alias.matches("[A-Za-z0-9_-]{3,32}")) {
            throw new IllegalArgumentException("customAlias must be 3-32 letters, digits, '_' or '-'");
        }
        return alias;
    }

    private String validateTarget(String value) {
        try {
            URI uri = URI.create(value.trim()).normalize();
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("url must be an absolute HTTP or HTTPS URL without embedded credentials");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("url must")) throw e;
            throw new IllegalArgumentException("url must be a valid absolute HTTP or HTTPS URL");
        }
    }

    private String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            URI uri = URI.create(referrer);
            return uri.getHost() == null ? null : uri.getHost().substring(0, Math.min(255, uri.getHost().length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unexpected daily analytics timestamp type: " + value.getClass().getName());
    }
}
