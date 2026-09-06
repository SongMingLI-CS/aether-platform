package com.aether.aether_backend.dto;

/**
 * A single hybrid-search hit: the atom plus its fused RRF score (higher is more
 * relevant). The score is informational - it lets clients order/explain results.
 */
public record SearchHitResponse(AtomResponse atom, double score) {
}
