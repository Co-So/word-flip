package com.wordflip.service;

import com.wordflip.domain.BookWord;
import com.wordflip.domain.UserWordLexicon;
import com.wordflip.dto.book.BookListResponse;
import com.wordflip.dto.settings.BooksSummary;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.BookRepository;
import com.wordflip.repository.BookWordRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.UserBookSelectionRepository;
import com.wordflip.repository.UserWordLexiconRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 词书列表与汇总计算（distinct 词数、estimatedGroupCount、unassignedCount）。
 */
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final UserBookSelectionRepository userBookSelectionRepository;
    private final BookWordRepository bookWordRepository;
    private final GroupWordRepository groupWordRepository;
    private final UserWordLexiconRepository userWordLexiconRepository;

    public BookService(
            BookRepository bookRepository,
            UserBookSelectionRepository userBookSelectionRepository,
            BookWordRepository bookWordRepository,
            GroupWordRepository groupWordRepository,
            UserWordLexiconRepository userWordLexiconRepository
    ) {
        this.bookRepository = bookRepository;
        this.userBookSelectionRepository = userBookSelectionRepository;
        this.bookWordRepository = bookWordRepository;
        this.groupWordRepository = groupWordRepository;
        this.userWordLexiconRepository = userWordLexiconRepository;
    }

    /** GET /books：builtin + 当前用户 imported，含 selected 标记 */
    @Transactional(readOnly = true)
    public BookListResponse listBooks(Long userId) {
        Set<Long> selectedIds = new HashSet<>(userBookSelectionRepository.findBookIdsByUserId(userId));
        List<BookListResponse.BookItem> items = bookRepository.findVisibleBooks(userId).stream()
                .map(book -> BookListResponse.BookItem.from(book, selectedIds.contains(book.getId())))
                .toList();
        return new BookListResponse(items);
    }

    /** 计算词书汇总（对齐 openapi BooksSummary） */
    @Transactional(readOnly = true)
    public BooksSummary buildSummary(Long userId, int groupSize) {
        List<Long> selectedBookIds = userBookSelectionRepository.findBookIdsByUserId(userId);
        if (selectedBookIds.isEmpty()) {
            return new BooksSummary(0, 0, 0);
        }

        Set<String> selectedWordKeys = new HashSet<>(bookWordRepository.findDistinctWordKeysByBookIds(selectedBookIds));
        Set<String> assignedWordKeys = groupWordRepository.findWordKeysByUserId(userId);

        long distinctCount = selectedWordKeys.size();
        long unassigned = selectedWordKeys.stream().filter(k -> !assignedWordKeys.contains(k)).count();
        int estimatedGroups = distinctCount == 0 ? 0 : (int) Math.ceil((double) distinctCount / groupSize);

        return new BooksSummary(distinctCount, estimatedGroups, unassigned);
    }

    /**
     * append 前对 delta wordKeys 批量 upsert lexicon（已存在不覆盖 cn/en）。
     */
    @Transactional
    public void upsertLexiconForNewWords(Long userId, List<String> wordKeys, List<Long> selectedBookIds) {
        if (wordKeys.isEmpty()) {
            return;
        }
        Set<String> existing = userWordLexiconRepository.findExistingWordKeys(userId, wordKeys);
        List<BookWord> sourceWords = bookWordRepository.findByBookIdsAndWordKeys(selectedBookIds, wordKeys);

        // 按 book_id、sort_order 取每个 wordKey 的首条作为来源
        Map<String, BookWord> firstByKey = new LinkedHashMap<>();
        for (BookWord bw : sourceWords) {
            firstByKey.putIfAbsent(bw.getWordKey(), bw);
        }

        for (String wordKey : wordKeys) {
            if (existing.contains(wordKey)) {
                continue;
            }
            BookWord source = firstByKey.get(wordKey);
            if (source == null) {
                continue;
            }
            UserWordLexicon lexicon = new UserWordLexicon();
            lexicon.setUserId(userId);
            lexicon.setWordKey(wordKey);
            lexicon.setEn(source.getEn());
            lexicon.setCn(source.getCn());
            lexicon.setPos(source.getPos());
            lexicon.setPh(source.getPh());
            lexicon.setDetailJson(source.getDetailJson());
            lexicon.setSourceBookId(source.getBookId());
            lexicon.setUpdatedAt(Instant.now());
            userWordLexiconRepository.save(lexicon);
        }
    }

    /** 校验 bookIds 对当前用户可见且存在 */
    @Transactional(readOnly = true)
    public void validateBookSelection(Long userId, List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return;
        }
        Set<Long> visibleIds = bookRepository.findVisibleBooks(userId).stream()
                .map(b -> b.getId())
                .collect(java.util.stream.Collectors.toSet());
        for (Long bookId : bookIds) {
            if (!visibleIds.contains(bookId)) {
                throw new WordflipException("NOT_FOUND", "词书不存在或不可访问: " + bookId);
            }
        }
    }
}
