package com.wordflip.service;

import com.wordflip.domain.GroupStrategy;
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
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分组只关联当前学习计划中的学习卡，不再关联全局 wordKey。
 */
@Service
public class GroupService {

    private static final String INSERT_GROUP_CARD_SQL =
            "INSERT INTO study_group_cards(group_id, plan_id, card_id, sort_order) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final LearningCardQueryService cards;
    private final AutoGroupCardOrderer autoGroupCardOrderer = new AutoGroupCardOrderer();

    public GroupService(JdbcTemplate jdbc, LearningCardQueryService cards) {
        this.jdbc = jdbc;
        this.cards = cards;
    }

    @Transactional(readOnly = true)
    public GroupListResponse listGroups(Long userId, String source, String sort) {
        validateListOptions(source, sort);
        Long planId = currentPlanId(userId);
        String order = "name".equals(sort)
                ? "g.name, g.id" : "g.created_at DESC, g.id DESC";
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
        validatePage(page, size);
        requireOwnedGroup(userId, groupId);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_group_cards WHERE group_id=?", Long.class, groupId
        );
        List<Long> ids = jdbc.queryForList(
                "SELECT card_id FROM study_group_cards WHERE group_id=? ORDER BY sort_order LIMIT ? OFFSET ?",
                Long.class, groupId, size, (page - 1) * size
        );
        List<LearningCardDetailResponse> values = ids.stream()
                .map(cardId -> cards.getCurrentCard(userId, cardId)).toList();
        long count = total == null ? 0 : total;
        return new GroupCardsResponse(
                page, size, count, (int) Math.ceil((double) count / size), values
        );
    }

    @Transactional(readOnly = true)
    public UnassignedCardsResponse listUnassignedCards(
            Long userId, boolean all, String query, int page, int size
    ) {
        validatePage(page, size);
        Long planId = currentPlanId(userId);
        int fetchSize = all ? 5000 : size;
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
                Long.class, planId, pattern, pattern, fetchSize, all ? 0 : (page - 1) * size
        );
        List<LearningCardDetailResponse> values = ids.stream()
                .map(cardId -> cards.getCurrentCard(userId, cardId)).toList();
        long count = total == null ? 0 : total;
        // all=true 对外表示不分页的单页结果，真实总数仍用于告知候选池规模。
        int responsePage = all ? 1 : page;
        int totalPages = all
                ? (count == 0 ? 0 : 1) : (int) Math.ceil((double) count / fetchSize);
        return new UnassignedCardsResponse(
                responsePage, fetchSize, count, totalPages, values
        );
    }

    @Transactional
    public GroupDetail createCustomGroup(Long userId, CreateCustomGroupRequest request) {
        // 服务层直接调用同样先处理空请求，避免无效选择触发当前计划查询。
        Set<Long> cardIds = request == null || request.cardIds() == null
                ? Set.of() : new LinkedHashSet<>(request.cardIds());
        if (cardIds.isEmpty() || cardIds.contains(null)) {
            throw new WordflipException("VALIDATION_ERROR", "至少选择一张学习卡");
        }
        Long planId = currentPlanId(userId);
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
        List<Object[]> memberAssignments = new ArrayList<>(cardIds.size());
        int cardOrder = 0;
        for (Long cardId : cardIds) {
            memberAssignments.add(new Object[]{groupId, planId, cardId, cardOrder++});
        }
        insertGroupCards(memberAssignments);
        return loadGroup(userId, groupId);
    }

    /**
     * 为计划增量追加自动分组；已有分组和记忆状态均不重建。
     */
    @Transactional
    public void appendAutoGroups(Long userId, Long planId) {
        // 单一锁定查询同时验证当前计划归属并固定设置快照，串行化同一计划的自动补组。
        List<Map<String, Object>> currentPlans = jdbc.queryForList(
                """
                SELECT p.id AS plan_id, us.group_size, us.group_strategy
                FROM user_settings us
                JOIN user_learning_plans p
                  ON p.id=us.active_plan_id AND p.user_id=us.user_id
                WHERE us.user_id=? AND us.active_plan_id=?
                FOR UPDATE
                """,
                userId, planId
        );
        if (currentPlans.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "学习计划不存在");
        }
        Map<String, Object> settings = currentPlans.getFirst();
        int chunkSize = ((Number) settings.get("group_size")).intValue();
        GroupStrategy strategy = GroupStrategy.valueOf((String) settings.get("group_strategy"));
        // NOT EXISTS 是幂等快路径，最终仍由 (plan_id, card_id) 唯一约束防止并发重复。
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT c.id AS card_id, bi.sort_order AS book_order,
                       CAST(JSON_UNQUOTE(JSON_EXTRACT(bi.metadata_json, '$.frequencyRank')) AS UNSIGNED)
                           AS frequency_rank
                FROM user_learning_plans p
                JOIN book_items bi ON bi.book_id=p.book_id
                JOIN learning_cards c ON c.book_item_id=bi.id AND c.status='published'
                WHERE p.id=? AND NOT EXISTS(
                  SELECT 1 FROM study_group_cards x WHERE x.plan_id=p.id AND x.card_id=c.id
                )
                """,
                planId
        );
        List<AutoGroupCardOrderer.Candidate> unassigned = autoGroupCardOrderer.order(
                rows.stream().map(row -> new AutoGroupCardOrderer.Candidate(
                        ((Number) row.get("card_id")).longValue(),
                        ((Number) row.get("book_order")).intValue(),
                        row.get("frequency_rank") == null
                                ? null : ((Number) row.get("frequency_rank")).intValue()
                )).toList(),
                strategy,
                userId,
                planId
        );
        if (unassigned.isEmpty()) {
            return;
        }

        int candidateIndex = 0;
        List<Object[]> memberAssignments = new ArrayList<>(unassigned.size());
        // 数据库只返回排序最末的未满自动组，自定义组和已满组均不参与续填。
        List<Map<String, Object>> lastAutoGroups = jdbc.queryForList(
                """
                SELECT g.id AS group_id, COUNT(sgc.id) AS group_size, g.sort_order AS sort_order
                FROM study_groups g
                LEFT JOIN study_group_cards sgc ON sgc.group_id=g.id
                WHERE g.plan_id=? AND g.source='auto'
                GROUP BY g.id, g.sort_order
                HAVING COUNT(sgc.id) < ?
                ORDER BY g.sort_order DESC, g.id DESC
                LIMIT 1
                """,
                planId, chunkSize
        );
        if (!lastAutoGroups.isEmpty()) {
            Map<String, Object> lastGroup = lastAutoGroups.getFirst();
            long groupId = ((Number) lastGroup.get("group_id")).longValue();
            int currentSize = ((Number) lastGroup.get("group_size")).intValue();
            // 只向最后一个未满的自动组尾部追加，不移动任何已有成员。
            while (currentSize < chunkSize && candidateIndex < unassigned.size()) {
                memberAssignments.add(new Object[]{
                        groupId, planId, unassigned.get(candidateIndex++).cardId(), currentSize++
                });
            }
        }

        if (candidateIndex >= unassigned.size()) {
            insertGroupCards(memberAssignments);
            return;
        }
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1)+1 FROM study_groups WHERE plan_id=?",
                Integer.class, planId
        );
        int groupOrder = next == null ? 0 : next;
        while (candidateIndex < unassigned.size()) {
            int currentOrder = groupOrder++;
            jdbc.update(
                    "INSERT INTO study_groups(plan_id, name, source, sort_order) VALUES (?, ?, 'auto', ?)",
                    planId, "第 " + (currentOrder + 1) + " 组", currentOrder
            );
            // 同一事务复用连接，直接读取刚插入组的自增主键，避免按非唯一排序字段反查。
            Long groupId = jdbc.queryForObject(
                    "SELECT LAST_INSERT_ID()", Long.class
            );
            int cardOrder = 0;
            while (cardOrder < chunkSize && candidateIndex < unassigned.size()) {
                memberAssignments.add(new Object[]{
                        groupId, planId, unassigned.get(candidateIndex++).cardId(), cardOrder++
                });
            }
        }
        insertGroupCards(memberAssignments);
    }

    /**
     * 在同一事务中批量写入组成员，避免大词书按卡产生数千次数据库往返。
     */
    private void insertGroupCards(List<Object[]> memberAssignments) {
        if (!memberAssignments.isEmpty()) {
            jdbc.batchUpdate(INSERT_GROUP_CARD_SQL, memberAssignments);
        }
    }

    private GroupDetail loadGroup(Long userId, Long groupId) {
        requireOwnedGroup(userId, groupId);
        // 最近一次听写事件决定掌握结果；热力仍只由稳定性阈值分档，二者不可混用。
        return jdbc.queryForObject(
                """
                SELECT g.id, g.name, g.source, g.created_at,
                       COUNT(sgc.id) AS total,
                       SUM(CASE WHEN m.reps>0 THEN 1 ELSE 0 END) AS reviewed,
                       SUM(CASE WHEN m.state='review' AND m.stability>=80
                                      AND m.scheduled_days>=30 AND r.correct=TRUE
                                THEN 1 ELSE 0 END) AS mastered,
                       SUM(CASE WHEN COALESCE(m.stability,0)=0 THEN 1 ELSE 0 END) AS heat0,
                       SUM(CASE WHEN m.stability>0 AND m.stability<3 THEN 1 ELSE 0 END) AS heat1,
                       SUM(CASE WHEN m.stability>=3 AND m.stability<15 THEN 1 ELSE 0 END) AS heat2,
                       SUM(CASE WHEN m.stability>=15 AND m.stability<30 THEN 1 ELSE 0 END) AS heat3,
                       SUM(CASE WHEN m.stability>=30 THEN 1 ELSE 0 END) AS heat4
                  FROM study_groups g
                  LEFT JOIN study_group_cards sgc ON sgc.group_id=g.id
                  LEFT JOIN card_skill_memory m ON m.card_id=sgc.card_id
                   AND m.user_id=? AND m.skill='dictation'
                  LEFT JOIN review_events r
                    ON r.user_id=m.user_id AND r.plan_id=g.plan_id
                   AND r.card_id=m.card_id AND r.skill=m.skill
                   AND r.id=(
                       SELECT r2.id FROM review_events r2
                        WHERE r2.user_id=? AND r2.plan_id=g.plan_id
                          AND r2.card_id=m.card_id AND r2.skill=m.skill
                        ORDER BY r2.answered_at DESC, r2.id DESC LIMIT 1
                   )
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
                userId, userId, groupId
        );
    }

    private void validateListOptions(String source, String sort) {
        // 白名单在读取当前计划前校验，避免非法排序值触发任何数据库访问。
        if (source != null && !Set.of("auto", "custom").contains(source)) {
            throw new WordflipException("VALIDATION_ERROR", "source 只允许 auto 或 custom");
        }
        if (!Set.of("createdAt", "name").contains(sort)) {
            throw new WordflipException("VALIDATION_ERROR", "sort 只允许 createdAt 或 name");
        }
    }

    private void validatePage(int page, int size) {
        // all=true 只改变内部抓取上限，不能绕过调用方分页参数契约。
        if (page < 1 || size < 1 || size > 100) {
            throw new WordflipException("VALIDATION_ERROR", "page 须从 1 开始且 size 须在 1–100");
        }
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
