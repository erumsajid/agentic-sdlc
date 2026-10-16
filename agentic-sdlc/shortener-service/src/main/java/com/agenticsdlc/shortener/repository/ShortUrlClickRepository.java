package com.agenticsdlc.shortener.repository;

import com.agenticsdlc.shortener.entity.ShortUrl;
import com.agenticsdlc.shortener.entity.ShortUrlClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ShortUrlClickRepository extends JpaRepository<ShortUrlClick, Long> {
    long countByShortUrlId(UUID shortUrlId);

    @Query("select c.referrerHost, count(c) from ShortUrlClick c " +
            "where c.shortUrl.id = :urlId and c.referrerHost is not null " +
            "group by c.referrerHost order by count(c) desc")
    List<Object[]> countByReferrer(@Param("urlId") UUID urlId);

    @Query("select function('date_trunc', 'day', c.accessedAt), count(c) from ShortUrlClick c " +
            "where c.shortUrl.id = :urlId and c.accessedAt >= :since " +
            "group by function('date_trunc', 'day', c.accessedAt) order by function('date_trunc', 'day', c.accessedAt)")
    List<Object[]> countDailySince(@Param("urlId") UUID urlId, @Param("since") Instant since);
}
