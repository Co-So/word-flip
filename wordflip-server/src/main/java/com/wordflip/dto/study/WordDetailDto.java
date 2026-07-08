package com.wordflip.dto.study;

import java.util.List;

/**
 * 单词详情抽屉，对齐 openapi WordCard.detail。
 */
public record WordDetailDto(String meaning, List<String> examples, String etymology) {
}
