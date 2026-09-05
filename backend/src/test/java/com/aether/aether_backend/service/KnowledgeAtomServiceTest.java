package com.aether.aether_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aether.aether_backend.common.exception.BusinessException;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.dto.AtomUpdateRequest;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;

@ExtendWith(MockitoExtension.class)
class KnowledgeAtomServiceTest {

    @Mock
    private KnowledgeAtomRepository repository;

    @InjectMocks
    private KnowledgeAtomService service;

    @Test
    void create_usesBuilderAndPersists() {
        when(repository.save(any(KnowledgeAtom.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAtom saved = service.create(new AtomCreateRequest("hello world", "TEXT"));

        assertThat(saved.getContentText()).isEqualTo("hello world");
        assertThat(saved.getContentType()).isEqualTo("TEXT");
        // isDeleted / createdAt are filled by JPA lifecycle callbacks (@PrePersist),
        // which are exercised in the integration tests, not with a mocked repository.
        verify(repository).save(any(KnowledgeAtom.class));
    }

    @Test
    void getById_missingAtom_throwsBusinessException() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识原子不存在");
    }

    @Test
    void update_emptyRequest_isRejected() {
        assertThatThrownBy(() -> service.update(1L, new AtomUpdateRequest(null, null)))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_blankContent_isRejected() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("orig", "TEXT").build();
        when(repository.findById(1L)).thenReturn(Optional.of(atom));

        assertThatThrownBy(() -> service.update(1L, new AtomUpdateRequest("   ", null)))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_happyPath_updatesFieldsAndSaves() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("orig", "TEXT").build();
        when(repository.findById(1L)).thenReturn(Optional.of(atom));
        when(repository.save(any(KnowledgeAtom.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAtom updated = service.update(1L, new AtomUpdateRequest("new content", "MARKDOWN"));

        assertThat(updated.getContentText()).isEqualTo("new content");
        assertThat(updated.getContentType()).isEqualTo("MARKDOWN");
        verify(repository).save(atom);
    }

    @Test
    void delete_fetchesAtomAndDeletesLogically() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("x", "TEXT").build();
        when(repository.findById(1L)).thenReturn(Optional.of(atom));

        service.delete(1L);

        verify(repository).delete(atom);
    }
}
