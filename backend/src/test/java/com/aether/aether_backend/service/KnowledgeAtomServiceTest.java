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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.aether.aether_backend.common.api.PageResult;
import com.aether.aether_backend.common.api.PageUtil;
import com.aether.aether_backend.common.exception.BusinessException;
import com.aether.aether_backend.domain.ContentType;
import com.aether.aether_backend.domain.KnowledgeAtom;
import com.aether.aether_backend.domain.event.AtomCreatedEvent;
import com.aether.aether_backend.domain.event.AtomDeletedEvent;
import com.aether.aether_backend.domain.event.AtomUpdatedEvent;
import com.aether.aether_backend.dto.AtomCreateRequest;
import com.aether.aether_backend.dto.AtomResponse;
import com.aether.aether_backend.dto.AtomUpdateRequest;
import com.aether.aether_backend.repository.KnowledgeAtomRepository;

@ExtendWith(MockitoExtension.class)
class KnowledgeAtomServiceTest {

    @Mock
    private KnowledgeAtomRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private KnowledgeAtomService service;

    @Test
    void create_usesBuilderPersistsAndPublishesEvent() {
        when(repository.save(any(KnowledgeAtom.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAtom saved = service.create(new AtomCreateRequest("hello world", ContentType.TEXT));

        assertThat(saved.getContentText()).isEqualTo("hello world");
        assertThat(saved.getContentType()).isEqualTo(ContentType.TEXT);
        // isDeleted / createdAt are filled by JPA lifecycle callbacks (@PrePersist),
        // which are exercised in the integration tests, not with a mocked repository.
        verify(repository).save(any(KnowledgeAtom.class));
        verify(eventPublisher).publishEvent(any(AtomCreatedEvent.class));
    }

    @Test
    void getById_missingAtom_throwsBusinessException() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识原子不存在");
    }

    @Test
    void list_delegatesWithClampedPageSizeAndOptionalFilters() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("alpha", ContentType.TEXT).build();
        Pageable pageable = PageRequest.of(1, PageUtil.MAX_PAGE_SIZE);
        when(repository.search(ContentType.TEXT, "alp", pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(atom), pageable, 200L));

        PageResult<AtomResponse> result = service.list(1, 500, ContentType.TEXT, "  alp  ");

        assertThat(result.totalElements()).isEqualTo(200);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).contentText()).isEqualTo("alpha");
        verify(repository).search(ContentType.TEXT, "alp", pageable);
    }

    @Test
    void update_emptyRequest_isRejected() {
        assertThatThrownBy(() -> service.update(1L, new AtomUpdateRequest(null, null)))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_blankContent_isRejected() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("orig", ContentType.TEXT).build();
        when(repository.findById(1L)).thenReturn(Optional.of(atom));

        assertThatThrownBy(() -> service.update(1L, new AtomUpdateRequest("   ", null)))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_happyPath_updatesFieldsAndSaves() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("orig", ContentType.TEXT).build();
        when(repository.findById(1L)).thenReturn(Optional.of(atom));
        when(repository.save(any(KnowledgeAtom.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAtom updated = service.update(1L,
                new AtomUpdateRequest("new content", ContentType.MARKDOWN));

        assertThat(updated.getContentText()).isEqualTo("new content");
        assertThat(updated.getContentType()).isEqualTo(ContentType.MARKDOWN);
        verify(repository).save(atom);
    }

    @Test
    void update_contentChanged_publishesUpdatedEvent() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("orig", ContentType.TEXT).build();
        atom.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(atom));
        when(repository.save(any(KnowledgeAtom.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(7L, new AtomUpdateRequest("changed", null));

        verify(eventPublisher).publishEvent(any(AtomUpdatedEvent.class));
    }

    @Test
    void update_contentTypeOnly_doesNotPublishUpdatedEvent() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("orig", ContentType.TEXT).build();
        atom.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(atom));
        when(repository.save(any(KnowledgeAtom.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(7L, new AtomUpdateRequest(null, ContentType.MARKDOWN));

        verify(eventPublisher, never()).publishEvent(any(AtomUpdatedEvent.class));
    }

    @Test
    void delete_publishesDeletedEvent() {
        KnowledgeAtom atom = new KnowledgeAtom.Builder("x", ContentType.TEXT).build();
        when(repository.findById(1L)).thenReturn(Optional.of(atom));

        service.delete(1L);

        verify(repository).delete(atom);
        verify(eventPublisher).publishEvent(any(AtomDeletedEvent.class));
    }
}
