package com.wordflip.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * dict_words 复合主键 (dict_id, word_key)。
 */
public class DictWordId implements Serializable {

    private String dictId;
    private String wordKey;

    public DictWordId() {
    }

    public DictWordId(String dictId, String wordKey) {
        this.dictId = dictId;
        this.wordKey = wordKey;
    }

    public String getDictId() {
        return dictId;
    }

    public void setDictId(String dictId) {
        this.dictId = dictId;
    }

    public String getWordKey() {
        return wordKey;
    }

    public void setWordKey(String wordKey) {
        this.wordKey = wordKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DictWordId that)) {
            return false;
        }
        return Objects.equals(dictId, that.dictId) && Objects.equals(wordKey, that.wordKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dictId, wordKey);
    }
}
