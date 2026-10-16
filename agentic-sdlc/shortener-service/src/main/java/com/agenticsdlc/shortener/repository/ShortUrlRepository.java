package com.agenticsdlc.shortener.repository;

import com.agenticsdlc.shortener.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {
    Optional<ShortUrl> findByCode(String code);
    boolean existsByCode(String code);

    @Modifying
    @Query("update ShortUrl u set u.clickCount = u.clickCount + 1 where u.id = :id")
    int incrementClickCount(@Param("id") UUID id);
}
