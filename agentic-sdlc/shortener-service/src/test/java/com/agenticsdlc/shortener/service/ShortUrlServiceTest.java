package com.agenticsdlc.shortener.service;

import com.agenticsdlc.shortener.dto.CreateShortUrlRequest;
import com.agenticsdlc.shortener.entity.ShortUrl;
import com.agenticsdlc.shortener.repository.ShortUrlClickRepository;
import com.agenticsdlc.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {
    @Mock private ShortUrlRepository urls;
    @Mock private ShortUrlClickRepository clicks;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        service = new ShortUrlService(urls, clicks, "http://localhost:8081/");
    }

    @Test
    void createsShortUrlWithCustomAlias() {
        when(urls.existsByCode("my-link")).thenReturn(false);
        when(urls.saveAndFlush(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateShortUrlRequest("https://example.com/path", "my-link", null));

        assertThat(response.code()).isEqualTo("my-link");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8081/my-link");
        assertThat(response.targetUrl()).isEqualTo("https://example.com/path");
    }

    @Test
    void rejectsNonHttpTargetsBeforeWriting() {
        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("javascript:alert(1)", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(urls, never()).saveAndFlush(any(ShortUrl.class));
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest(
                "https://user:password@example.com/path", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without embedded credentials");
    }

    @Test
    void rejectsDuplicateAliases() {
        when(urls.existsByCode("my-link")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest(
                "https://example.com/path", "my-link", null)))
                .isInstanceOf(AliasConflictException.class);

        verify(urls, never()).saveAndFlush(any(ShortUrl.class));
    }

    @Test
    void redirectRecordsClickAndReturnsTarget() {
        UUID id = UUID.randomUUID();
        ShortUrl shortUrl = new ShortUrl(id, "my-link", "https://example.com/path", Instant.now(), null);
        when(urls.findByCode("my-link")).thenReturn(Optional.of(shortUrl));
        when(urls.incrementClickCount(id)).thenReturn(1);

        assertThat(service.resolveAndRecord("my-link", "https://news.example/article"))
                .isEqualTo("https://example.com/path");

        verify(urls).incrementClickCount(id);
        verify(clicks).save(any());
    }

    @Test
    void expiredLinksAreNotRedirectedOrCounted() {
        ShortUrl shortUrl = new ShortUrl(UUID.randomUUID(), "old-link", "https://example.com",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        when(urls.findByCode("old-link")).thenReturn(Optional.of(shortUrl));

        assertThatThrownBy(() -> service.resolveAndRecord("old-link", null))
                .isInstanceOf(ShortUrlNotFoundException.class);

        verify(clicks, never()).save(any());
    }
}
