package com.agenticsdlc.shortener.controller;

import com.agenticsdlc.shortener.dto.AnalyticsResponse;
import com.agenticsdlc.shortener.dto.CreateShortUrlRequest;
import com.agenticsdlc.shortener.dto.ShortUrlResponse;
import com.agenticsdlc.shortener.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@RequestMapping("/api/urls")
public class ShortUrlController {
    private final ShortUrlService service;

    public ShortUrlController(ShortUrlService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@RequestBody CreateShortUrlRequest request) {
        try {
            ShortUrlResponse response = service.create(request);
            return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (com.agenticsdlc.shortener.service.AliasConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/{code}/analytics")
    public AnalyticsResponse analytics(@PathVariable String code) {
        try {
            return service.analytics(code);
        } catch (com.agenticsdlc.shortener.service.ShortUrlNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        try {
            String target = service.resolveAndRecord(code, request.getHeader(HttpHeaders.REFERER));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
        } catch (com.agenticsdlc.shortener.service.ShortUrlNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
