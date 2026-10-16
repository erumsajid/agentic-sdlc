package com.agenticsdlc.shortener.service;

public class ShortUrlNotFoundException extends RuntimeException {
    public ShortUrlNotFoundException(String message) { super(message); }
}
