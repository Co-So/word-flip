package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.dto.stain.StainBatchRequest;
import com.wordflip.dto.stain.StainBatchResponse;
import com.wordflip.dto.stain.StainConfig;
import com.wordflip.dto.stain.StainUpdateRequest;
import com.wordflip.dto.stain.WordStainResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.util.StableHash;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习卡污渍服务；默认配置可重复生成，只有用户修改时才落库。
 */
@Service
public class StainService {

    private static final Set<String> ACTIONS = Set.of(
            "regenerate", "set_hidden", "set_visible", "replace"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StainService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WordStainResponse getStain(Long userId, Long cardId) {
        Long lexemeId = requireCurrentCard(userId, cardId);
        List<WordStainResponse> rows = jdbc.query(
                "SELECT hidden, config_json FROM card_stains WHERE user_id=? AND card_id=?",
                (rs, row) -> new WordStainResponse(
                        cardId, lexemeId, rs.getBoolean("hidden"), readConfig(rs.getString("config_json"))
                ), userId, cardId
        );
        return rows.isEmpty()
                ? new WordStainResponse(cardId, lexemeId, false, defaultConfig(userId, cardId))
                : rows.getFirst();
    }

    @Transactional
    public WordStainResponse updateStain(Long userId, Long cardId, StainUpdateRequest request) {
        requireCurrentCard(userId, cardId);
        String action = request.getAction() == null ? "" : request.getAction().trim().toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            throw new WordflipException("VALIDATION_ERROR", "无效的 action: " + request.getAction());
        }
        WordStainResponse current = getStain(userId, cardId);
        boolean hidden = current.hidden();
        StainConfig config = current.config();
        switch (action) {
            case "regenerate" -> config = new StainConfig(ThreadLocalRandom.current().nextLong());
            case "set_hidden" -> hidden = true;
            case "set_visible" -> hidden = false;
            case "replace" -> {
                if (request.getConfig() == null || request.getConfig().getSeed() == null) {
                    throw new WordflipException("VALIDATION_ERROR", "replace 须提供含 seed 的 config");
                }
                config = request.getConfig();
            }
            default -> throw new WordflipException("VALIDATION_ERROR", "无效的 action");
        }
        jdbc.update(
                """
                INSERT INTO card_stains(user_id, card_id, hidden, config_json)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE hidden=VALUES(hidden), config_json=VALUES(config_json), updated_at=NOW(3)
                """,
                userId, cardId, hidden, writeConfig(config)
        );
        return getStain(userId, cardId);
    }

    @Transactional
    public StainBatchResponse batchRegenerate(Long userId, Long groupId, StainBatchRequest request) {
        Integer owned = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM study_groups g
                JOIN user_learning_plans p ON p.id=g.plan_id
                JOIN user_settings us ON us.active_plan_id=p.id AND us.user_id=p.user_id
                WHERE g.id=? AND p.user_id=?
                """,
                Integer.class, groupId, userId
        );
        if (owned == null || owned == 0) {
            throw new WordflipException("NOT_FOUND", "当前计划中没有该分组");
        }
        List<Long> cardIds = jdbc.queryForList(
                "SELECT card_id FROM study_group_cards WHERE group_id=? ORDER BY sort_order",
                Long.class, groupId
        );
        for (Long cardId : cardIds) {
            jdbc.update(
                    """
                    INSERT INTO card_stains(user_id, card_id, hidden, config_json)
                    VALUES (?, ?, FALSE, ?)
                    ON DUPLICATE KEY UPDATE config_json=VALUES(config_json), updated_at=NOW(3)
                    """,
                    userId, cardId, writeConfig(new StainConfig(ThreadLocalRandom.current().nextLong()))
            );
        }
        return new StainBatchResponse(groupId, cardIds.size());
    }

    private Long requireCurrentCard(Long userId, Long cardId) {
        List<Long> values = jdbc.queryForList(
                """
                SELECT bi.lexeme_id FROM learning_cards c
                JOIN book_items bi ON bi.id=c.book_item_id
                JOIN user_learning_plans p ON p.book_id=bi.book_id AND p.user_id=?
                JOIN user_settings us ON us.user_id=p.user_id AND us.active_plan_id=p.id
                WHERE c.id=? AND c.status='published'
                """,
                Long.class, userId, cardId
        );
        if (values.isEmpty()) {
            throw new WordflipException("NOT_FOUND", "当前计划中没有该学习卡");
        }
        return values.getFirst();
    }

    private StainConfig defaultConfig(Long userId, Long cardId) {
        return new StainConfig(StableHash.defaultStainSeed(userId, String.valueOf(cardId)));
    }

    private StainConfig readConfig(String json) {
        try {
            return objectMapper.readValue(json, StainConfig.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("污渍配置解析失败", error);
        }
    }

    private String writeConfig(StainConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("污渍配置序列化失败", error);
        }
    }
}
