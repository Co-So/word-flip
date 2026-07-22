package com.wordflip.service;

/**
 * 单次服务端 FSRS 调度结果。
 */
public record FsrsScheduleResult(
        String rating,
        FsrsMemorySnapshot before,
        FsrsMemorySnapshot after
) {
}
