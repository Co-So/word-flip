package com.wordflip.service;

import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupCardsResponse;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupListResponse;
import com.wordflip.dto.group.GroupStats;
import com.wordflip.dto.group.UnassignedCardsResponse;
import com.wordflip.dto.learning.LearningCardDetailResponse;
import com.wordflip.exception.WordflipException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分组只关联当前学习计划中的学习卡，不再关联全局 wordKey。
 */
@Service
public class GroupService {

    private final JdbcTemplate jdbc;
    private final LearningCardQueryService cards;

    public GroupService(JdbcTemplate jdbc, LearningCardQueryService cards) {
        this.jdbc = jdbc;
        this.cards = cards;
    }

    @Transactional(readOnly = true)
    public GroupListResponse listGroups(Long userId, String source, String sort) {
        Long planId = currentPlanId(userId);
        String order = "name".equals(sort) ? "g.name, g.id" : "g.sort_order, g.id";
        String sql = "SELECT g.id FROM study_groups g WHERE g.plan_id=?"
                + (source == null ? "" : " AND g.source=?") + " ORDER BY " + order;
        List<Long> ids = source == null
                ? jdbc.queryForList(sql, Long.class, planId)
                : jdbc.queryForList(sql, Long.class, planId, source);
        return new GroupListResponse(ids.stream().map(id -> loadGroup(userId, id)).toList());
    }

    @Transactional(readOnly = true)
    public GroupDetail getGroup(Long userId, Long groupId) {
        return loadGroup(userId, groupId);
    }

    @Transactional(readOnly = true)
    public GroupCardsResponse listGroupCards(Long userId, Long groupId, int page, int size) {
        requireOwnedGroup(userId, groupId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_group_cards WHERE group_id=?", Long.class, groupId
        );
        List<Long> ids = jdbc.queryForList(
                "SELECT card_id FROM study_group_cards WHERE group_id=? ORDER BY sort_order LIMIT ? OFFSET ?",
                Long.class, groupId, safeSize, (safePage - 1) * safeSize
        );
        List<LearningCardDetailResponse> values = ids.stream()
                .map(cardId -> cards.getCurrentCard(userId, cardId)).toList();
        long count = total == null ? 0 : total;
        return new GroupCardsResponse(
                safePage, safeSize, count, (int) Math.ceil((double) count / safeSize), values
        );
    }

    @Transactional(readOnly = true)
    public UnassignedCardsResponse listUnassignedCards(
            Long userId, boolean all, String query, int page, int size
    ) {
        Long planId = currentPlanId(userId);
        int safePage = Math.max(page, 1);
        int safeSize = all ? 5000 : Math.min(Math.max(size, 1), 100);
        String pattern = query == null || query.isBlank() ? "%" : query.trim() + "%";
        String base = """
                FROM user_learning_plans p
                JOIN book_items bi ON bi.book_id=p.book_id
                JOIN learning_cards c ON c.book_item_id=bi.id AND c.status='published'
                JOIN lexemes l ON l.id=bi.lexeme_id
                WHERE p.id=? AND NOT EXISTS(
                  SELECT 1 FROM study_group_cards sgc WHERE sgc.plan_id=p.id AND sgc.card_id=c.id
                ) AND (l.headword LIKE ? OR EXISTS(
                  SELECT 1 FROM learning_card_senses s WHERE s.card_id=c.id AND s.cn LIKE ?
                ))
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + base, Long.class, planId, pattern, pattern);
        List<Long> ids = jdbc.queryForList(
                "SELECT c.id " + base + " ORDER BY bi.sort_order LIMIT ? OFFSET ?",
                Long.class, planId, pattern, pattern, safeSize, all ? 0 : (safePage - 1) * safeSize
        );
        List<LearningCardDetailResponse> values = ids.stream()
                .map(cardId -> cards.getCurrentCard(userId, cardId)).toList();
        long count = total == null ? 0 : total;
        return new UnassignedCardsResponse(
                safePage, safeSize, count, (int) Math.ceil((double) count / safeSize), values
        );
    }

    @Transactional
    public GroupDetail createCustomGroup(Long userId, CreateCustomGroupRequest request) {
        Long planId = currentPlanId(userId);
        Set<Long> cardIds = new LinkedHashSet<>(request.cardIds());
        if (cardIds.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "至少选择一张学习卡");
        }
        for (Long cardId : cardIds) {
            Integer valid = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM user_learning_plans p
                    JOIN book_items bi ON bi.book_id=p.book_id
                    JOIN learning_cards c ON c.book_item_id=bi.id AND c.status='published'
                    WHERE p.id=? AND c.id=? AND NOT EXISTS(
                      SELECT 1 FROM study_group_cards x WHERE x.plan_id=p.id AND x.card_id=c.id
                    )
                    """,
                    Integer.class, planId, cardId
            );
            if (valid == null || valid == 0) {
                throw new WordflipException("CONFLICT", "学习卡已入组或不属于当前计划: " + cardId);
            }
        }
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1)+1 FROM study_groups WHERE plan_id=?",
                Integer.class, planId
        );
        int sortOrder = next == null ? 0 : next;
        String name = request.name() == null || request.name().isBlank()
                ? "自定义分组 " + (sortOrder + 1) : request.name().trim();
        jdbc.update(
                "INSERT INTO study_groups(plan_id, name, source, sort_order) VALUES (?, ?, 'custom', ?)",
                planId, name, sortOrder
        );
        Long groupId = jdbc.queryForObject(
                "SELECT id FROM study_groups WHERE plan_id=? AND sort_order=?", Long.class, planId, sortOrder
        );
        int cardOrder = 0;
        for (Long cardId : cardIds) {
            jdbc.update(
                    "INSERT INTO study_group_cards(group_id, plan_id, card_id, sort_order) VALUES (?, ?, ?, ?)",
                    groupId, planId, cardId, cardOrder++
            );
        }
        return loadGroup(userId, groupId);
    }

    /**
     * 为计划增量追加自动分组；已有分组和记忆状态均不重建。
     */
    @Transactional
    public void appendAutoGroups(Long userId, Long planId) {
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_learning_plans WHERE id=? AND user_id=?",
                Integer.class, planId, userId
        );
        if (owned == null || owned == 0) {
            throw new WordflipException("NOT_FOUND", "学习计划不存在");
        }
        Integer groupSize = jdbc.queryForObject(
                "SELECT group_size FROM user_settings WHERE user_id=?", Integer.class, userId
        );
        int chunkSize = groupSize == null ? 20 : groupSize;
        List<Long> unassigned = jdbc.queryForList(
                """
                SELECT c.id FROM user_learning_plans p
                JOIN book_items bi ON bi.book_id=p.book_id
                JOIN learning_cards c ON c.book_item_id=bi.id AND c.status='published'
                WHERE p.id=? AND NOT EXISTS(
                  SELECT 1 FROM study_group_cards x WHERE x.plan_id=p.id AND x.card_id=c.id
                ) ORDER BY bi.sort_order
                """,
                Long.class, planId
        );
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1)+1 FROM study_groups WHERE plan_id=?",
                Integer.class, planId
        );
        int groupOrder = next == null ? 0 : next;
        for (int offset = 0; offset < unassigned.size(); offset += chunkSize) {
            int currentOrder = groupOrder++;
            jdbc.update(
                    "INSERT INTO study_groups(plan_id, name, source, sort_order) VALUES (?, ?, 'auto', ?)",
                    planId, "第 " + (currentOrder + 1) + " 组", currentOrder
            );
            Long groupId = jdbc.queryForObject(
                    "SELECT id FROM study_groups WHERE plan_id=? AND sort_order=?", Long.class, planId, currentOrder
            );
            List<Long> chunk = unassigned.subList(offset, Math.min(offset + chunkSize, unassigned.size()));
            for (int index = 0; index < chunk.size(); index++) {
                jdbc.update(
                        "INSERT INTO study_group_cards(group_id, plan_id, card_id, sort_order) VALUES (?, ?, ?, ?)",
                        groupId, planId, chunk.get(index), index
                );
            }
        }
    }

    private GroupDetail loadGroup(Long userId, Long groupId) {
        requireOwnedGroup(userId, groupId);
        return jdbc.queryForObject(
                """
                SELECT g.id, g.name, g.source, g.created_at,
                       COUNT(sgc.id) AS total,
                       SUM(CASE WHEN m.reps>0 THEN 1 ELSE 0 END) AS reviewed,
                       SUM(CASE WHEN m.stability>=30 THEN 1 ELSE 0 END) AS mastered,
                       SUM(CASE WHEN COALESCE(m.stability,0)=0 THEN 1 ELSE 0 END) AS heat0,
                       SUM(CASE WHEN m.stability>0 AND m.stability<3 THEN 1 ELSE 0 END) AS heat1,
                       SUM(CASE WHEN m.stability>=3 AND m.stability<15 THEN 1 ELSE 0 END) AS heat2,
                       SUM(CASE WHEN m.stability>=15 AND m.stability<30 THEN 1 ELSE 0 END) AS heat3,
                       SUM(CASE WHEN m.stability>=30 THEN 1 ELSE 0 END) AS heat4
                  FROM study_groups g
                  LEFT JOIN study_group_cards sgc ON sgc.group_id=g.id
                  LEFT JOIN card_skill_memory m ON m.card_id=sgc.card_id
                   AND m.user_id=? AND m.skill='dictation'
                 WHERE g.id=? GROUP BY g.id, g.name, g.source, g.created_at
                """,
                (rs, row) -> {
                    int total = rs.getInt("total");
                    int reviewed = rs.getInt("reviewed");
                    int mastered = rs.getInt("mastered");
                    String status = reviewed == 0 ? "not_started"
                            : mastered == total && total > 0 ? "completed" : "learning";
                    float progress = total == 0 ? 0 : (float) mastered / total;
                    return new GroupDetail(
                            rs.getLong("id"), rs.getString("name"), rs.getString("source"), status,
                            rs.getTimestamp("created_at").toInstant(),
                            new GroupStats(
                                    rs.getInt("heat0"), rs.getInt("heat1"), rs.getInt("heat2"),
                                    rs.getInt("heat3"), rs.getInt("heat4"), total
                            ),
                            progress
                    );
                },
                userId, groupId
        );
    }

    private void requireOwnedGroup(Long userId, Long groupId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM study_groups g
                JOIN user_learning_plans p ON p.id=g.plan_id
                JOIN user_settings us ON us.active_plan_id=p.id AND us.user_id=p.user_id
                WHERE g.id=? AND p.user_id=?
                """,
                Integer.class, groupId, userId
        );
        if (count == null || count == 0) {
            throw new WordflipException("NOT_FOUND", "当前学习计划中没有该分组");
        }
    }

    private Long currentPlanId(Long userId) {
        List<Long> values = jdbc.queryForList(
                "SELECT active_plan_id FROM user_settings WHERE user_id=? AND active_plan_id IS NOT NULL",
                Long.class, userId
        );
        if (values.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "尚未选择当前学习计划");
        }
        return values.getFirst();
    }
}
