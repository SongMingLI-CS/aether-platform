package com.aether.aether_backend.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaEmbeddingClientTest {

    @Test
    void embed_parsesOllamaEmbeddingArray() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:11434/api/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"embedding\":[1.0,2.0,3.0]}", MediaType.APPLICATION_JSON));

        OllamaEmbeddingClient client = new OllamaEmbeddingClient(builder, "http://localhost:11434", "bge-m3", 3);

        float[] vector = client.embed("hello world");

        assertThat(vector).containsExactly(1.0f, 2.0f, 3.0f);
        server.verify();
    }

    @Test
    void embed_blankText_returnsZeroVectorWithoutCallingOllama() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        OllamaEmbeddingClient client = new OllamaEmbeddingClient(builder, "http://localhost:11434", "bge-m3", 4);

        float[] vector = client.embed("   ");

        assertThat(vector).hasSize(4).containsOnly(0.0f);
        server.verify();
    }

    @Test
    void dimensions_returnsConfiguredValue() {
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(RestClient.builder(), "http://x", "bge-m3", 1024);
        assertThat(client.dimensions()).isEqualTo(1024);
    }
}
