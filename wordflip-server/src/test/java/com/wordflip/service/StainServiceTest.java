package com.wordflip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.WordStain;
import com.wordflip.dto.stain.StainBatchRequest;
import com.wordflip.dto.stain.StainBatchResponse;
import com.wordflip.dto.stain.StainUpdateRequest;
import com.wordflip.dto.stain.WordStainResponse;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.WordStainRepository;
import com.wordflip.util.StableHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StainService 单测：默认 seed 不落库、regenerate 持久化、set_hidden。
 */
@ExtendWith(MockitoExtension.class)
class StainServiceTest {

    private static final Long USER_ID = 1L;
    private static final String WORD_KEY = "apple";

    @Mock
    private WordStainRepository wordStainRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupWordRepository groupWordRepository;

    private StainService stainService;

    @BeforeEach
    void setUp() {
        stainService = new StainService(
                wordStainRepository,
                groupRepository,
                groupWordRepository,
                new ObjectMapper()
        );
    }

    @Test
    void getStain_withoutRow_returnsDefaultSeedAndDoesNotSave() {
        when(wordStainRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.empty());

        WordStainResponse response = stainService.getStain(USER_ID, WORD_KEY);

        assertThat(response.wordKey()).isEqualTo(WORD_KEY);
        assertThat(response.hidden()).isFalse();
        assertThat(response.config()).isNotNull();
        assertThat(response.config().getSeed()).isEqualTo(StableHash.defaultStainSeed(USER_ID, WORD_KEY));
        verify(wordStainRepository, never()).save(any());
    }

    @Test
    void regenerate_persistsNewConfig() {
        when(wordStainRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.empty());
        when(wordStainRepository.save(any(WordStain.class))).thenAnswer(inv -> inv.getArgument(0));

        StainUpdateRequest request = new StainUpdateRequest();
        request.setAction("regenerate");

        WordStainResponse response = stainService.updateStain(USER_ID, WORD_KEY, request);

        ArgumentCaptor<WordStain> captor = ArgumentCaptor.forClass(WordStain.class);
        verify(wordStainRepository).save(captor.capture());
        WordStain saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getWordKey()).isEqualTo(WORD_KEY);
        assertThat(saved.getStainConfigJson()).isNotBlank();
        assertThat(response.config()).isNotNull();
        assertThat(response.config().getSeed()).isNotNull();
        assertThat(response.config().getStains()).isNotEmpty();
        assertThat(response.config().getMode()).isEqualTo("random");
    }

    @Test
    void setHidden_persistsHiddenTrue() {
        when(wordStainRepository.findByUserIdAndWordKey(USER_ID, WORD_KEY)).thenReturn(Optional.empty());
        when(wordStainRepository.save(any(WordStain.class))).thenAnswer(inv -> inv.getArgument(0));

        StainUpdateRequest request = new StainUpdateRequest();
        request.setAction("set_hidden");

        WordStainResponse response = stainService.updateStain(USER_ID, WORD_KEY, request);

        ArgumentCaptor<WordStain> captor = ArgumentCaptor.forClass(WordStain.class);
        verify(wordStainRepository).save(captor.capture());
        assertThat(captor.getValue().isHidden()).isTrue();
        assertThat(response.hidden()).isTrue();
        assertThat(captor.getValue().getStainConfigJson()).isNotBlank();
    }

    @Test
    void batchRegenerate_updatesAllGroupWords() {
        StudyGroup group = new StudyGroup();
        group.setId(9L);
        group.setUserId(USER_ID);
        when(groupRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(group));

        GroupWord gw1 = new GroupWord();
        gw1.setWordKey("apple");
        GroupWord gw2 = new GroupWord();
        gw2.setWordKey("banana");
        when(groupWordRepository.findByGroupIdOrderBySortOrderAsc(9L)).thenReturn(List.of(gw1, gw2));
        when(wordStainRepository.findByUserIdAndWordKey(any(), any())).thenReturn(Optional.empty());
        when(wordStainRepository.save(any(WordStain.class))).thenAnswer(inv -> inv.getArgument(0));

        StainBatchResponse response = stainService.batchRegenerate(USER_ID, 9L, new StainBatchRequest());

        assertThat(response.groupId()).isEqualTo(9L);
        assertThat(response.updatedCount()).isEqualTo(2);
        verify(wordStainRepository, org.mockito.Mockito.times(2)).save(any(WordStain.class));
    }
}
