package com.wordflip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.config.LexiconProperties;
import com.wordflip.domain.BookWord;
import com.wordflip.domain.DictSense;
import com.wordflip.domain.DictSenseQuality;
import com.wordflip.domain.DictWord;
import com.wordflip.domain.Dictionary;
import com.wordflip.domain.DictionaryIds;
import com.wordflip.domain.DictionaryLocale;
import com.wordflip.domain.UserSettings;
import com.wordflip.domain.UserWordLexicon;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.DictExampleRepository;
import com.wordflip.repository.DictSenseRepository;
import com.wordflip.repository.DictWordRepository;
import com.wordflip.repository.DictionaryRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserSettingsRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WordLookup 按 activeDictId 读 dict；无合格 primary 时回退 lexicon。
 */
@ExtendWith(MockitoExtension.class)
class WordLookupServiceTest {

    @Mock
    private UserWordLexiconRepository userWordLexiconRepository;
    @Mock
    private UserBookSelectionRepository userBookSelectionRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private BookWordRepository bookWordRepository;
    @Mock
    private DictWordRepository dictWordRepository;
    @Mock
    private DictSenseRepository dictSenseRepository;
    @Mock
    private DictExampleRepository dictExampleRepository;
    @Mock
    private DictionaryRepository dictionaryRepository;

    private LexiconProperties lexiconProperties;
    private WordLookupService service;

    @BeforeEach
    void setUp() {
        lexiconProperties = new LexiconProperties();
        lexiconProperties.setSource("dict");
        service = new WordLookupService(
                userWordLexiconRepository,
                userBookSelectionRepository,
                userSettingsRepository,
                bookWordRepository,
                dictWordRepository,
                dictSenseRepository,
                dictExampleRepository,
                dictionaryRepository,
                lexiconProperties,
                new ObjectMapper()
        );
        UserSettings settings = new UserSettings();
        settings.setActiveDictId(DictionaryIds.CURATED);
        org.mockito.Mockito.lenient().when(userSettingsRepository.findById(any()))
                .thenReturn(Optional.of(settings));
        Dictionary curated = new Dictionary();
        curated.setId(DictionaryIds.CURATED);
        curated.setLocale(DictionaryLocale.zh);
        org.mockito.Mockito.lenient().when(dictionaryRepository.findById(DictionaryIds.CURATED))
                .thenReturn(Optional.of(curated));
        Dictionary wordnet = new Dictionary();
        wordnet.setId(DictionaryIds.WORDNET);
        wordnet.setLocale(DictionaryLocale.en);
        org.mockito.Mockito.lenient().when(dictionaryRepository.findById(DictionaryIds.WORDNET))
                .thenReturn(Optional.of(wordnet));
    }

    @Test
    void resolve_prefersDictPrimaryOk() {
        DictWord head = new DictWord();
        head.setDictId(DictionaryIds.CURATED);
        head.setWordKey("be");
        head.setEn("be");
        head.setPh("/biː/");

        DictSense primary = new DictSense();
        primary.setId(1L);
        primary.setDictId(DictionaryIds.CURATED);
        primary.setWordKey("be");
        primary.setPos("v.");
        primary.setCn("是, 表示, 在");
        primary.setPrimary(true);
        primary.setQuality(DictSenseQuality.ok);
        primary.setSortOrder(0);

        when(dictWordRepository.findByDictIdAndWordKeyIn(eq(DictionaryIds.CURATED), anyCollection()))
                .thenReturn(List.of(head));
        when(dictSenseRepository.findByDictIdAndWordKeyInOrdered(eq(DictionaryIds.CURATED), anyCollection()))
                .thenReturn(List.of(primary));
        when(dictExampleRepository.findBySenseIdInOrderBySenseIdAscSortOrderAsc(anyCollection()))
                .thenReturn(List.of());

        Map<String, WordSummary> map = service.resolveWordSummaries(1L, List.of("be"));

        assertThat(map).containsKey("be");
        assertThat(map.get("be").cn()).isEqualTo("是, 表示, 在");
        assertThat(map.get("be").senses()).hasSize(1);
        assertThat(map.get("be").senses().getFirst().primary()).isTrue();
        verify(userWordLexiconRepository, never()).findByUserIdAndWordKeyIn(any(), any());
    }

    @Test
    void resolve_skipsRejectPrimary_fallsBackToLexicon() {
        DictWord head = new DictWord();
        head.setDictId(DictionaryIds.CURATED);
        head.setWordKey("junk");
        head.setEn("junk");

        DictSense reject = new DictSense();
        reject.setId(2L);
        reject.setDictId(DictionaryIds.CURATED);
        reject.setWordKey("junk");
        reject.setCn("坏数据");
        reject.setPrimary(true);
        reject.setQuality(DictSenseQuality.reject);
        reject.setSortOrder(0);

        when(dictWordRepository.findByDictIdAndWordKeyIn(eq(DictionaryIds.CURATED), anyCollection()))
                .thenReturn(List.of(head));
        when(dictSenseRepository.findByDictIdAndWordKeyInOrdered(eq(DictionaryIds.CURATED), anyCollection()))
                .thenReturn(List.of(reject));

        UserWordLexicon lexicon = new UserWordLexicon();
        lexicon.setUserId(1L);
        lexicon.setWordKey("junk");
        lexicon.setEn("junk");
        lexicon.setCn("垃圾 (n.)");
        when(userWordLexiconRepository.findByUserIdAndWordKeyIn(eq(1L), any())).thenReturn(List.of(lexicon));

        Map<String, WordSummary> map = service.resolveWordSummaries(1L, List.of("junk"));

        assertThat(map.get("junk").cn()).isEqualTo("垃圾");
        assertThat(map.get("junk").senses()).isEmpty();
    }

    @Test
    void resolve_legacySource_skipsDict() {
        lexiconProperties.setSource("legacy");
        UserWordLexicon lexicon = new UserWordLexicon();
        lexicon.setUserId(1L);
        lexicon.setWordKey("time");
        lexicon.setEn("time");
        lexicon.setCn("时间");
        when(userWordLexiconRepository.findByUserIdAndWordKeyIn(eq(1L), any())).thenReturn(List.of(lexicon));

        Map<String, WordSummary> map = service.resolveWordSummaries(1L, List.of("time"));

        assertThat(map.get("time").cn()).isEqualTo("时间");
        verify(dictWordRepository, never()).findByDictIdAndWordKeyIn(any(), any());
    }

    @Test
    void resolve_wordnetUsesEnGloss() {
        UserSettings settings = new UserSettings();
        settings.setActiveDictId(DictionaryIds.WORDNET);
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));

        DictWord head = new DictWord();
        head.setDictId(DictionaryIds.WORDNET);
        head.setWordKey("go");
        head.setEn("go");

        DictSense primary = new DictSense();
        primary.setId(9L);
        primary.setDictId(DictionaryIds.WORDNET);
        primary.setWordKey("go");
        primary.setPos("v.");
        primary.setEnGloss("change location; move, travel, or proceed");
        primary.setPrimary(true);
        primary.setQuality(DictSenseQuality.ok);

        when(dictWordRepository.findByDictIdAndWordKeyIn(eq(DictionaryIds.WORDNET), anyCollection()))
                .thenReturn(List.of(head));
        when(dictSenseRepository.findByDictIdAndWordKeyInOrdered(eq(DictionaryIds.WORDNET), anyCollection()))
                .thenReturn(List.of(primary));
        when(dictExampleRepository.findBySenseIdInOrderBySenseIdAscSortOrderAsc(anyCollection()))
                .thenReturn(List.of());

        Map<String, WordSummary> map = service.resolveWordSummaries(1L, List.of("go"));
        assertThat(map.get("go").enGloss()).contains("change location");
        assertThat(map.get("go").cn()).isNull();
    }

    @Test
    void resolve_examSenseOverridesDisplayPrimary() {
        DictWord head = new DictWord();
        head.setDictId(DictionaryIds.CURATED);
        head.setWordKey("but");
        head.setEn("but");

        DictSense prep = new DictSense();
        prep.setId(10L);
        prep.setDictId(DictionaryIds.CURATED);
        prep.setWordKey("but");
        prep.setPos("prep.");
        prep.setCn("除了");
        prep.setPrimary(true);
        prep.setQuality(DictSenseQuality.ok);
        prep.setSortOrder(0);

        DictSense conj = new DictSense();
        conj.setId(11L);
        conj.setDictId(DictionaryIds.CURATED);
        conj.setWordKey("but");
        conj.setPos("conj.");
        conj.setCn("但是");
        conj.setPrimary(false);
        conj.setQuality(DictSenseQuality.ok);
        conj.setSortOrder(1);

        when(dictWordRepository.findByDictIdAndWordKeyIn(eq(DictionaryIds.CURATED), anyCollection()))
                .thenReturn(List.of(head));
        when(dictSenseRepository.findByDictIdAndWordKeyInOrdered(eq(DictionaryIds.CURATED), anyCollection()))
                .thenReturn(List.of(prep, conj));
        when(dictExampleRepository.findBySenseIdInOrderBySenseIdAscSortOrderAsc(anyCollection()))
                .thenReturn(List.of());
        when(userBookSelectionRepository.findBookIdsByUserId(1L)).thenReturn(List.of(99L));

        BookWord bw = new BookWord();
        bw.setBookId(99L);
        bw.setWordKey("but");
        bw.setExamSenseId(11L);
        when(bookWordRepository.findByBookIdsAndWordKeys(anyCollection(), anyCollection()))
                .thenReturn(List.of(bw));
        when(dictSenseRepository.findByIdIn(anyCollection())).thenReturn(List.of(conj));

        Map<String, WordSummary> map = service.resolveWordSummaries(1L, List.of("but"));

        assertThat(map.get("but").cn()).isEqualTo("但是");
        assertThat(map.get("but").pos()).isEqualTo("conj.");
        assertThat(map.get("but").senses().stream().filter(s -> s.primary()).findFirst())
                .get()
                .extracting(s -> s.id())
                .isEqualTo(11L);
    }
}
