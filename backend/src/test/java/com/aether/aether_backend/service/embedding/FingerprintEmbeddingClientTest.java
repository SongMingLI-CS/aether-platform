package com.aether.aether_backend.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FingerprintEmbeddingClientTest {

    private final FingerprintEmbeddingClient client = new FingerprintEmbeddingClient();

    @Test
    void embed_isDeterministicAndFixedSize() {
        float[] first = client.embed("Spring Boot 是流行的 Java 后端框架");
        float[] second = client.embed("Spring Boot 是流行的 Java 后端框架");

        assertThat(first).hasSize(FingerprintEmbeddingClient.DIMENSIONS);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void embed_blankText_returnsZeroVector() {
        float[] vector = client.embed("   ");

        assertThat(vector).containsOnly(0.0f);
    }

    @Test
    void similarTexts_scoreHigherThanDifferentTexts() {
        float[] a = client.embed("Spring Boot 微服务实践笔记 第一版");
        float[] b = client.embed("Spring Boot 微服务实践笔记 第二版");
        float[] c = client.embed("量子计算与拓扑绝缘体物理");

        double similar = cosine(a, b);
        double different = cosine(a, c);

        assertThat(similar).isGreaterThan(0.5);
        assertThat(different).isLessThan(similar);
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
