package com.wordflip.dto.study;

import java.time.LocalDate;

/**
 * POST /study/sessions 响应体。
 */
public record StudySessionReportResponse(LocalDate logDate, int streakDays) {
}
