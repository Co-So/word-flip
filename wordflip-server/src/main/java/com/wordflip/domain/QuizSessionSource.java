package com.wordflip.domain;

/**
 * 测验入口来源，对齐 openapi CreateQuizSessionRequest.source。
 */
public enum QuizSessionSource {
    today,
    study,
    retry,
    groups,
    all,
    recent
}
