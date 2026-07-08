package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 全局英文词频序（word_freq_ranks 表）；供 GroupStrategy.frequency 排序。
 */
@Entity
@Table(name = "word_freq_ranks")
@Getter
@Setter
public class WordFreqRank {

    @Id
    @Column(name = "word_key", nullable = false, length = 128)
    private String wordKey;

    @Column(name = "freq_rank", nullable = false, columnDefinition = "INT UNSIGNED")
    private int freqRank;

    @Column(nullable = false, length = 32)
    private String source = "wordfreq";
}
