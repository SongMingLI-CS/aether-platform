package com.aether.aether_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.dto.AtomResponse;
import com.aether.aether_backend.dto.AtomUpdateRequest;
import com.aether.aether_backend.service.KnowledgeAtomService;

/**
 * Contract tests for /api/v1/atoms: unified envelope, HTTP semantics,
 * validation and error mapping.
 */
@WebMvcTest(AtomController.class)
class AtomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeAtomService service;

    @Test
    void create_returns201WithCreatedAtom() throws Exception {
        when(service.create(any(AtomCreateRequest.class))).thenReturn(atom(1L, "hello"));

        mockMvc.perform(post("/api/v1/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"hello\",\"contentType\":\"TEXT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.contentType").value("TEXT"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void create_invalidContentType_returns400WithHint() throws Exception {
        mockMvc.perform(post("/api/v1/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"hello\",\"contentType\":\"HTML\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("contentType 仅支持 TEXT / MARKDOWN / IMAGE_URL"));
    }

    @Test
    void create_blankContent_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"  \",\"contentType\":\"TEXT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void list_returnsPageInsideEnvelope() throws Exception {
        when(service.list(eq(0), eq(20), eq(null), eq(null)))
                .thenReturn(new PageResult<>(1, 1, 0, 20, List.of(AtomResponse.from(atom(7L, "foo")))));

        mockMvc.perform(get("/api/v1/atoms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(7));
    }

    @Test
    void list_invalidContentTypeParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/atoms").param("contentType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void getById_returnsAtom() throws Exception {
        when(service.getById(9L)).thenReturn(atom(9L, "detail"));

        mockMvc.perform(get("/api/v1/atoms/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentText").value("detail"));
    }

    @Test
    void update_patchesAtom() throws Exception {
        when(service.update(eq(3L), any(AtomUpdateRequest.class))).thenReturn(atom(3L, "patched"));

        mockMvc.perform(patch("/api/v1/atoms/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentText\":\"patched\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentText").value("patched"));
    }

    @Test
    void delete_returnsOk() throws Exception {
        doNothing().when(service).delete(5L);

        mockMvc.perform(delete("/api/v1/atoms/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private static KnowledgeAtom atom(long id, String contentText) {
        KnowledgeAtom atom = new KnowledgeAtom.Builder(contentText, ContentType.TEXT).build();
        atom.setId(id);
        atom.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        atom.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        atom.setVersion(0L);
        return atom;
    }
}