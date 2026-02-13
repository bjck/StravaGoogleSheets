package com.bko.fitnessextractor.ai.domain;

/**
 * Minimal chat message representation that is provider-agnostic.
 */
public record ChatMessage(Role role, String content) {
    public enum Role { SYSTEM, USER }
}

