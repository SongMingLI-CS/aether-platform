package com.aether.aether_backend.common.api;

/**
 * Shared pagination constants and clamping so every service pages consistently.
 */
public final class PageUtil {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageUtil() {
    }

    public static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
