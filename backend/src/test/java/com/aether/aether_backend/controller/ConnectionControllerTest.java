package com.aether.aether_backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.dto.ConnectionResponse;
import com.aether.aether_backend.service.KnowledgeConnectionService;

@WebMvcTest(ConnectionController.class)
class ConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeConnectionService service;

    @Test
    void list_returnsPageInsideEnvelope() throws Exception {
        ConnectionResponse connection = new ConnectionResponse(
                1L, 1L, "note a", 2L, "note b", 0.92,
                ConnectionStatus.PENDING, "similar", java.time.Instant.parse("2026-01-01T00:00:00Z"));
        when(service.list(eq(0), eq(20), eq(null), eq(null)))
                .thenReturn(new PageResult<>(1, 1, 0, 20, List.of(connection)));

        mockMvc.perform(get("/api/v1/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].similarity").value(0.92))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    void listForAtom_returnsAtomConnections() throws Exception {
        when(service.listForAtom(eq(7L), eq(0), eq(20)))
                .thenReturn(new PageResult<>(0, 0, 0, 20, List.of()));

        mockMvc.perform(get("/api/v1/atoms/7/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
