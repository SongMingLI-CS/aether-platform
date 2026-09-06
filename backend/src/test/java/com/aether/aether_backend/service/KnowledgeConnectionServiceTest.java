package com.aether.aether_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aether.aether_backend.common.exception.BusinessException;
import com.aether.aether_backend.domain.ConnectionOrigin;
import com.aether.aether_backend.domain.ConnectionStatus;
import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.KnowledgeConnection;
import com.aether.aether_backend.dto.ConnectionResponse;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;
import com.aether.aether_backend.repository.KnowledgeConnectionRepository;

@ExtendWith(MockitoExtension.class)
class KnowledgeConnectionServiceTest {

    @Mock
    private KnowledgeConnectionRepository connectionRepository;

    @Mock
    private KnowledgeAtomRepository atomRepository;

    @InjectMocks
    private KnowledgeConnectionService service;

    @Test
    void updateStatus_confirm_updatesAndReturnsResponse() {
        KnowledgeConnection connection = connection(1L);
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        when(atomRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(atom(1L, "note a"), atom(2L, "note b")));

        ConnectionResponse response = service.updateStatus(1L, ConnectionStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(ConnectionStatus.CONFIRMED);
        assertThat(response.sourceText()).isEqualTo("note a");
        assertThat(response.targetText()).isEqualTo("note b");
        verify(connectionRepository).save(connection);
    }

    @Test
    void updateStatus_ignored_updatesStatus() {
        KnowledgeConnection connection = connection(1L);
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        when(atomRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of());

        ConnectionResponse response = service.updateStatus(1L, ConnectionStatus.IGNORED);

        assertThat(response.status()).isEqualTo(ConnectionStatus.IGNORED);
        verify(connectionRepository).save(connection);
    }

    @Test
    void updateStatus_pending_isRejected() {
        assertThatThrownBy(() -> service.updateStatus(1L, ConnectionStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONFIRMED 或 IGNORED");
    }

    @Test
    void updateStatus_missingConnection_isRejected() {
        when(connectionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, ConnectionStatus.IGNORED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("连接不存在");
    }

    @Test
    void createManual_createsConfirmedManualConnection() {
        when(atomRepository.existsById(1L)).thenReturn(true);
        when(atomRepository.existsById(2L)).thenReturn(true);
        when(connectionRepository.findByAtomPair(1L, 2L)).thenReturn(Optional.empty());
        when(connectionRepository.save(any(KnowledgeConnection.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(atomRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(atom(1L, "note a"), atom(2L, "note b")));

        // Reversed input order → normalized to (minId, maxId).
        ConnectionResponse response = service.createManual(2L, 1L);

        assertThat(response.status()).isEqualTo(ConnectionStatus.CONFIRMED);
        assertThat(response.origin()).isEqualTo(ConnectionOrigin.MANUAL);
        assertThat(response.similarity()).isEqualTo(1.0);
        assertThat(response.sourceAtomId()).isEqualTo(1L);
        assertThat(response.targetAtomId()).isEqualTo(2L);
    }

    @Test
    void createManual_selfConnect_isRejected() {
        assertThatThrownBy(() -> service.createManual(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能连接同一个");
    }

    @Test
    void createManual_revivesPendingToConfirmed() {
        KnowledgeConnection existing = connection(1L);
        when(atomRepository.existsById(1L)).thenReturn(true);
        when(atomRepository.existsById(2L)).thenReturn(true);
        when(connectionRepository.findByAtomPair(1L, 2L)).thenReturn(Optional.of(existing));
        when(atomRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(atom(1L, "note a"), atom(2L, "note b")));

        service.createManual(1L, 2L);

        assertThat(existing.getStatus()).isEqualTo(ConnectionStatus.CONFIRMED);
        assertThat(existing.getOrigin()).isEqualTo(ConnectionOrigin.MANUAL);
        assertThat(existing.getSimilarity()).isEqualTo(1.0);
        verify(connectionRepository).save(existing);
    }

    private KnowledgeConnection connection(long id) {
        KnowledgeConnection c = new KnowledgeConnection(1L, 2L, 0.9, ConnectionStatus.PENDING, "r");
        c.setId(id);
        return c;
    }

    private KnowledgeAtom atom(long id, String text) {
        KnowledgeAtom a = new KnowledgeAtom.Builder(text, ContentType.TEXT).build();
        a.setId(id);
        return a;
    }
}
