package com.aether.aether_backend.service.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class QdrantVectorStoreTest {

    private static final String BASE = "http://localhost:6333";
    private static final String COLLECTION = "aether_atoms";

    @Test
    void upsertThenSearch_returnsNearestHits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/collections/" + COLLECTION))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        server.expect(requestTo(BASE + "/collections/" + COLLECTION + "/points?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        server.expect(requestTo(BASE + "/collections/" + COLLECTION + "/points/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"result\":[{\"id\":2,\"score\":0.92},{\"id\":3,\"score\":0.41}]}",
                        MediaType.APPLICATION_JSON));

        QdrantVectorStore store = new QdrantVectorStore(builder, BASE, COLLECTION);
        store.upsert(1L, new float[]{1.0f, 0.0f});

        List<ScoredAtom> hits = store.search(new float[]{1.0f, 0.0f}, 5);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).atomId()).isEqualTo(2L);
        assertThat(hits.get(0).score()).isGreaterThan(0.9);
        server.verify();
    }

    @Test
    void search_withoutUpsert_returnsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        QdrantVectorStore store = new QdrantVectorStore(builder, BASE, COLLECTION);

        assertThat(store.search(new float[]{1.0f}, 5)).isEmpty();
        server.verify();
    }

    @Test
    void size_missingCollection_returnsZero() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/collections/" + COLLECTION))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        QdrantVectorStore store = new QdrantVectorStore(builder, BASE, COLLECTION);

        assertThat(store.size()).isZero();
        server.verify();
    }
}
