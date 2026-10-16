package com.agenticsdlc.shortener.service;

public class AliasConflictException extends RuntimeException {
    public AliasConflictException(String message) { super(message); }
}
