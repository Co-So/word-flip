package com.wordflip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.Book;
import com.wordflip.domain.BookSource;
import com.wordflip.dto.book.BookImportPreviewResponse;
import com.wordflip.dto.book.BookListResponse;
import com.wordflip.dto.book.BookWordsResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.BookRepository;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 词书详情/导入/删除单测。
 */
@ExtendWith(MockitoExtension.class)
class BookImportServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookWordRepository bookWordRepository;
    @Mock
    private UserBookSelectionRepository userBookSelectionRepository;
    @Mock
    private UserWordLexiconRepository userWordLexiconRepository;
    @Mock
    private GroupWordRepository groupWordRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private BookImportService importService;
    private BookService bookService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        importService = new BookImportService(
                bookRepository,
                bookWordRepository,
                userBookSelectionRepository,
                userWordLexiconRepository,
                redisTemplate,
                objectMapper
        );
        bookService = new BookService(
                bookRepository,
                userBookSelectionRepository,
                bookWordRepository,
                groupWordRepository,
                userWordLexiconRepository
        );
    }

    @Test
    void preview_parsesCsvAndStoresRedis() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.csv",
                "text/csv",
                "apple,苹果\nbanana,香蕉\napple,重复\n".getBytes(StandardCharsets.UTF_8)
        );

        BookImportPreviewResponse response = importService.preview(1L, file);

        assertThat(response.suggestedName()).isEqualTo("demo");
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.deduplicatedCount()).isEqualTo(1);
        assertThat(response.previewWords()).hasSize(2);
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    void preview_rejectsEmptyFile() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> importService.preview(1L, file))
                .isInstanceOf(WordflipException.class)
                .extracting(ex -> ((WordflipException) ex).getCode())
                .isEqualTo("PARSE_ERROR");
    }

    @Test
    void deleteImported_rejectsBuiltin() {
        Book builtin = new Book();
        builtin.setId(1L);
        builtin.setSource(BookSource.builtin);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(builtin));

        assertThatThrownBy(() -> importService.deleteImportedBook(9L, 1L))
                .isInstanceOf(WordflipException.class)
                .extracting(ex -> ((WordflipException) ex).getCode())
                .isEqualTo("FORBIDDEN");
        verify(bookRepository, never()).delete(any());
    }

    @Test
    void deleteImported_removesOwnBook() {
        Book imported = new Book();
        imported.setId(5L);
        imported.setSource(BookSource.imported);
        imported.setUserId(9L);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(imported));

        importService.deleteImportedBook(9L, 5L);

        verify(bookRepository).delete(imported);
    }

    @Test
    void getBook_returnsVisibleBuiltin() {
        Book builtin = new Book();
        builtin.setId(1L);
        builtin.setName("雅思");
        builtin.setSource(BookSource.builtin);
        builtin.setWordCount(100);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(builtin));
        when(userBookSelectionRepository.existsByIdUserIdAndIdBookId(2L, 1L)).thenReturn(true);

        BookListResponse.BookItem item = bookService.getBook(2L, 1L);

        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.selected()).isTrue();
        assertThat(item.canDelete()).isFalse();
    }

    @Test
    void listBookWords_paginates() {
        Book builtin = new Book();
        builtin.setId(1L);
        builtin.setSource(BookSource.builtin);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(builtin));

        com.wordflip.domain.BookWord bw = new com.wordflip.domain.BookWord();
        bw.setWordKey("apple");
        bw.setEn("apple");
        bw.setCn("苹果");
        when(bookWordRepository.findByBookIdOrderBySortOrderAsc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bw), Pageable.ofSize(20), 1));

        BookWordsResponse response = bookService.listBookWords(2L, 1L, 0, 20);

        assertThat(response.words()).hasSize(1);
        assertThat(response.words().getFirst().en()).isEqualTo("apple");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void getBook_hidesOthersImported() {
        Book other = new Book();
        other.setId(8L);
        other.setSource(BookSource.imported);
        other.setUserId(99L);
        when(bookRepository.findById(8L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> bookService.getBook(1L, 8L))
                .isInstanceOf(WordflipException.class)
                .extracting(ex -> ((WordflipException) ex).getCode())
                .isEqualTo("NOT_FOUND");
    }
}
