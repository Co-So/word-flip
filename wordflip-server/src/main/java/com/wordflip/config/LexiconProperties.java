package com.wordflip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 词库读源配置：dict=读 dict_* primary；legacy=仅 lexicon/book_words。
 */
@Component
@ConfigurationProperties(prefix = "wordflip.lexicon")
public class LexiconProperties {

    /**
     * dict | legacy
     */
    private String source = "dict";

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean useDict() {
        return source == null || !"legacy".equalsIgnoreCase(source.trim());
    }
}
