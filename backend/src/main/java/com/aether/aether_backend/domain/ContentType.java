package com.aether.aether_backend.domain;

/**
 * Allowed knowledge atom content types (see docs/db_schema_v0.1.md).
 * Stored as enum names (TEXT / MARKDOWN / IMAGE_URL) in the database.
 */
public enum ContentType {
    TEXT, MARKDOWN, IMAGE_URL
}
