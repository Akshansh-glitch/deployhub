package io.deployhub.config;

/** The authenticated principal placed in the security context by {@link JwtAuthFilter}. */
public record AuthUser(Long id, String email) {}
