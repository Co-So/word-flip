package com.wordflip.service;

import com.wordflip.dto.group.GroupCardsResponse;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.study.StudyGroupPayload;
import com.wordflip.dto.study.StudySessionReportRequest;
import com.wordflip.dto.study.StudySessionReportResponse;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习浏览服务；翻卡只读取学习卡并写 study_logs。
 */
@Service
public class StudyService {

    private final JdbcTemplate jdbc;
    private final GroupService groups;

    public StudyService(JdbcTemplate jdbc, GroupService groups) {
        this.jdbc = jdbc;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public StudyGroupPayload getStudyGroup(Long userId, Long groupId) {
        GroupDetail group = groups.getGroup(userId, groupId);
        GroupCardsResponse cards = groups.listGroupCards(userId, groupId, 1, 5000);
        return new StudyGroupPayload(
                new StudyGroupPayload.StudyGroupInfo(group.id(), group.name(), group.source()),
                cards.cards()
        );
    }

    /**
     * 记录浏览时长和卡片数；此事务不访问 card_skill_memory 或 lexeme_skill_memory。
     */
    @Transactional
    public StudySessionReportResponse reportSession(
            Long userId, StudySessionReportRequest request, ZoneId zoneId
    ) {
        groups.getGroup(userId, request.groupId());
        Long planId = jdbc.queryForObject(
                "SELECT active_plan_id FROM user_settings WHERE user_id=?", Long.class, userId
        );
        Instant completed = request.completedAt() == null ? Instant.now() : request.completedAt();
        LocalDate logDate = completed.atZone(zoneId).toLocalDate();
        int duration = request.durationSec() == null ? 0 : Math.max(0, request.durationSec());
        int viewed = request.cardsViewed() == null ? 0 : Math.max(0, request.cardsViewed());
        jdbc.update(
                """
                INSERT INTO study_logs(user_id, plan_id, group_id, log_date, duration_sec, cards_viewed, quiz_count)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """,
                userId, planId, request.groupId(), Date.valueOf(logDate), duration, viewed
        );
        return new StudySessionReportResponse(logDate, streakDays(userId, logDate));
    }

    private int streakDays(Long userId, LocalDate today) {
        List<LocalDate> dates = jdbc.query(
                "SELECT DISTINCT log_date FROM study_logs WHERE user_id=? AND log_date<=? ORDER BY log_date DESC",
                (rs, row) -> rs.getDate(1).toLocalDate(), userId, Date.valueOf(today)
        );
        int streak = 0;
        LocalDate expected = today;
        for (LocalDate date : dates) {
            if (!date.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }
}
