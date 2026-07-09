package com.wordflip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.GroupWord;
import com.wordflip.domain.StudyGroup;
import com.wordflip.domain.WordStain;
import com.wordflip.dto.stain.StainBatchRequest;
import com.wordflip.dto.stain.StainBatchResponse;
import com.wordflip.dto.stain.StainConfig;
import com.wordflip.dto.stain.StainItemDto;
import com.wordflip.dto.stain.StainUpdateRequest;
import com.wordflip.dto.stain.WordStainResponse;
import com.wordflip.exception.WordflipException;
import com.wordflip.repository.GroupRepository;
import com.wordflip.repository.GroupWordRepository;
import com.wordflip.repository.WordStainRepository;
import com.wordflip.util.StableHash;
import com.wordflip.util.WordKeyUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 污渍配置服务（REQ-STAIN-1~7）：默认 seed、regenerate、隐藏、分组 batch。
 * <p>
 * 无 {@code word_stains} 行时不落库，仅返回 {@code stableHash(userId + wordKey)} 的最小 config。
 */
@Service
public class StainService {

    private static final List<String> ALL_TYPES = List.of(
            "coffee", "ink", "highlight", "crayon", "random-line"
    );

    private static final Map<String, Integer> LAYER_ORDER = Map.of(
            "coffee", 1,
            "ink", 2,
            "highlight", 3,
            "crayon", 4,
            "random-line", 5
    );

    private static final Set<String> VALID_ACTIONS = Set.of(
            "regenerate", "set_hidden", "set_visible", "replace"
    );

    private final WordStainRepository wordStainRepository;
    private final GroupRepository groupRepository;
    private final GroupWordRepository groupWordRepository;
    private final ObjectMapper objectMapper;

    public StainService(
            WordStainRepository wordStainRepository,
            GroupRepository groupRepository,
            GroupWordRepository groupWordRepository,
            ObjectMapper objectMapper
    ) {
        this.wordStainRepository = wordStainRepository;
        this.groupRepository = groupRepository;
        this.groupWordRepository = groupWordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /words/{wordKey}/stain：有行则读库；无行返回默认 seed 且不 INSERT。
     */
    @Transactional(readOnly = true)
    public WordStainResponse getStain(Long userId, String rawWordKey) {
        String wordKey = WordKeyUtil.normalize(rawWordKey);
        if (wordKey.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "wordKey 不能为空");
        }
        Optional<WordStain> existing = wordStainRepository.findByUserIdAndWordKey(userId, wordKey);
        if (existing.isEmpty()) {
            // 无行：默认 seed = stableHash(userId + wordKey)，不持久化（P3-B06）
            return new WordStainResponse(wordKey, false, minimalDefaultConfig(userId, wordKey));
        }
        WordStain row = existing.get();
        return new WordStainResponse(wordKey, row.isHidden(), readConfigOrDefault(row, userId, wordKey));
    }

    /**
     * PUT /words/{wordKey}/stain：按 action 更新并持久化。
     */
    @Transactional
    public WordStainResponse updateStain(Long userId, String rawWordKey, StainUpdateRequest request) {
        String wordKey = WordKeyUtil.normalize(rawWordKey);
        if (wordKey.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "wordKey 不能为空");
        }
        String action = request.getAction() == null ? "" : request.getAction().trim().toLowerCase(Locale.ROOT);
        if (!VALID_ACTIONS.contains(action)) {
            throw new WordflipException("VALIDATION_ERROR", "无效的 action: " + request.getAction());
        }

        WordStain row = wordStainRepository.findByUserIdAndWordKey(userId, wordKey)
                .orElseGet(() -> newRow(userId, wordKey));

        switch (action) {
            case "regenerate" -> {
                // regenerate：新随机 seed，生成完整 config 并落库（REQ-STAIN-4）
                StainConfig config = generateConfig(
                        ThreadLocalRandom.current().nextLong(),
                        "random",
                        request.getTypeFilter(),
                        50,
                        20
                );
                row.setStainConfigJson(writeConfig(config));
            }
            case "set_hidden" -> {
                // REQ-STAIN-5：隐藏；若尚无 config 则写入默认 seed 最小配置以便有行可持久化 hidden
                row.setHidden(true);
                ensureConfigPersisted(row, userId, wordKey);
            }
            case "set_visible" -> {
                // REQ-STAIN-7：重新显示
                row.setHidden(false);
                ensureConfigPersisted(row, userId, wordKey);
            }
            case "replace" -> {
                if (request.getConfig() == null || request.getConfig().getSeed() == null) {
                    throw new WordflipException("VALIDATION_ERROR", "replace 须提供含 seed 的 config");
                }
                row.setStainConfigJson(writeConfig(request.getConfig()));
            }
            default -> throw new WordflipException("VALIDATION_ERROR", "无效的 action: " + action);
        }

        row.setUpdatedAt(Instant.now());
        wordStainRepository.save(row);
        return new WordStainResponse(wordKey, row.isHidden(), readConfigOrDefault(row, userId, wordKey));
    }

    /**
     * POST /groups/{groupId}/stains/batch：对组内每个词 regenerate 并落库。
     */
    @Transactional
    public StainBatchResponse batchRegenerate(Long userId, Long groupId, StainBatchRequest request) {
        requireOwnedGroup(userId, groupId);
        List<String> wordKeys = groupWordRepository.findByGroupIdOrderBySortOrderAsc(groupId).stream()
                .map(GroupWord::getWordKey)
                .toList();

        List<String> typeFilter = request != null ? request.getTypeFilter() : null;
        int updated = 0;
        for (String wordKey : wordKeys) {
            WordStain row = wordStainRepository.findByUserIdAndWordKey(userId, wordKey)
                    .orElseGet(() -> newRow(userId, wordKey));
            // batch：每词新随机 seed 并持久化（api-modules §2.4）
            StainConfig config = generateConfig(
                    ThreadLocalRandom.current().nextLong(),
                    "random",
                    typeFilter,
                    50,
                    20
            );
            row.setStainConfigJson(writeConfig(config));
            row.setUpdatedAt(Instant.now());
            wordStainRepository.save(row);
            updated++;
        }
        return new StainBatchResponse(groupId, updated);
    }

    private StudyGroup requireOwnedGroup(Long userId, Long groupId) {
        return groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new WordflipException("NOT_FOUND", "分组不存在"));
    }

    private WordStain newRow(Long userId, String wordKey) {
        WordStain row = new WordStain();
        row.setUserId(userId);
        row.setWordKey(wordKey);
        row.setHidden(false);
        row.setUpdatedAt(Instant.now());
        return row;
    }

    /** 隐藏/显示时若尚无 JSON，写入默认 seed 最小配置，避免空行无意义 */
    private void ensureConfigPersisted(WordStain row, Long userId, String wordKey) {
        if (row.getStainConfigJson() == null || row.getStainConfigJson().isBlank()) {
            row.setStainConfigJson(writeConfig(minimalDefaultConfig(userId, wordKey)));
        }
    }

    private StainConfig minimalDefaultConfig(Long userId, String wordKey) {
        return new StainConfig(StableHash.defaultStainSeed(userId, wordKey));
    }

    private StainConfig readConfigOrDefault(WordStain row, Long userId, String wordKey) {
        if (row.getStainConfigJson() == null || row.getStainConfigJson().isBlank()) {
            return minimalDefaultConfig(userId, wordKey);
        }
        try {
            return objectMapper.readValue(row.getStainConfigJson(), StainConfig.class);
        } catch (JsonProcessingException e) {
            throw new WordflipException("INTERNAL_ERROR", "污渍配置解析失败");
        }
    }

    private String writeConfig(StainConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new WordflipException("INTERNAL_ERROR", "污渍配置序列化失败");
        }
    }

    /**
     * 按 seed 确定性生成污渍列表；逻辑对齐 Android StainGenerator.generate（服务端权威落库）。
     */
    StainConfig generateConfig(
            long baseSeed,
            String mode,
            List<String> typeFilter,
            int density,
            int aging
    ) {
        List<String> types = resolveTypes(typeFilter);
        String resolvedMode = mode == null || mode.isBlank() ? "random" : mode.toLowerCase(Locale.ROOT);
        Random random = new Random(baseSeed);

        List<String> enabledTypes = switch (resolvedMode) {
            case "single" -> List.of(types.getFirst());
            case "multi" -> types;
            default -> {
                // random：随机取 2~5 种类型
                int count = 2 + random.nextInt(4);
                List<String> shuffled = new ArrayList<>(types);
                Collections.shuffle(shuffled, random);
                yield shuffled.subList(0, Math.min(count, shuffled.size()));
            }
        };

        List<StainItemDto> stains = new ArrayList<>();
        List<float[]> positions = new ArrayList<>();
        float minDist = 0.15f;

        for (int t = 0; t < enabledTypes.size(); t++) {
            String type = enabledTypes.get(t);
            int typeOrdinal = ALL_TYPES.indexOf(type);
            if (typeOrdinal < 0) {
                typeOrdinal = t;
            }
            int maxCount = 1 + random.nextInt(3);
            int stainCount = Math.max(1, (int) (maxCount * density / 100f));
            for (int i = 0; i < stainCount; i++) {
                long itemSeed = baseSeed + typeOrdinal * 100L + i * 1000L;
                float[] pos = weightedPosition(itemSeed, positions, minDist);
                if (pos == null) {
                    continue;
                }
                positions.add(pos);
                StainItemDto item = new StainItemDto();
                item.setType(type);
                item.setX((double) pos[0]);
                item.setY((double) pos[1]);
                item.setSize((double) randRange(20f, 55f, itemSeed + 50));
                item.setRotation((double) randRange(0f, (float) (Math.PI * 2), itemSeed + 51));
                item.setIntensity((double) randRange(0.5f, 1f, itemSeed + 52));
                item.setSeed(itemSeed);
                item.setLayerOrder(LAYER_ORDER.getOrDefault(type, 5));
                stains.add(item);
            }
        }

        stains.sort((a, b) -> Integer.compare(
                a.getLayerOrder() != null ? a.getLayerOrder() : 5,
                b.getLayerOrder() != null ? b.getLayerOrder() : 5
        ));

        StainConfig config = new StainConfig();
        config.setSeed(baseSeed);
        config.setMode(resolvedMode);
        config.setDensity(density);
        config.setAging(aging);
        config.setStains(stains);
        return config;
    }

    private List<String> resolveTypes(List<String> typeFilter) {
        if (typeFilter == null || typeFilter.isEmpty()) {
            return ALL_TYPES;
        }
        List<String> resolved = new ArrayList<>();
        for (String raw : typeFilter) {
            if (raw == null) {
                continue;
            }
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if (ALL_TYPES.contains(t) && !resolved.contains(t)) {
                resolved.add(t);
            }
        }
        if (resolved.isEmpty()) {
            throw new WordflipException("VALIDATION_ERROR", "typeFilter 无有效污渍类型");
        }
        return resolved;
    }

    /** 对齐 Android StainGenerator.weightedPosition：偏好边缘、避开中心文字区 */
    private float[] weightedPosition(long seed, List<float[]> existing, float minDist) {
        float[] best = null;
        float bestScore = -1f;
        for (int attempt = 0; attempt < 30; attempt++) {
            long s = seed + attempt * 7919L;
            float zone = seededRandom(s);
            float x;
            float y;
            float score;
            if (zone < 0.6f) {
                float margin = 0.05f;
                int corner = randInt(0, 3, s + 1L);
                score = 0.6f;
                switch (corner) {
                    case 0 -> {
                        x = randRange(margin, 0.25f, s + 2L);
                        y = randRange(margin, 0.3f, s + 3L);
                    }
                    case 1 -> {
                        x = randRange(0.75f, 1f - margin, s + 2L);
                        y = randRange(margin, 0.3f, s + 3L);
                    }
                    case 2 -> {
                        x = randRange(margin, 0.25f, s + 2L);
                        y = randRange(0.7f, 1f - margin, s + 3L);
                    }
                    default -> {
                        x = randRange(0.75f, 1f - margin, s + 2L);
                        y = randRange(0.7f, 1f - margin, s + 3L);
                    }
                }
            } else if (zone < 0.9f) {
                x = randRange(0.25f, 0.75f, s + 2L);
                y = randRange(0.25f, 0.75f, s + 3L);
                score = 0.3f;
            } else {
                x = randRange(0.15f, 0.85f, s + 2L);
                y = randRange(0.15f, 0.85f, s + 3L);
                score = 0.1f;
            }
            // REQ-STUDY-4 / REQ-STAIN-2：不遮挡主词中心区
            float adjusted = (x >= 0.3f && x <= 0.7f && y >= 0.35f && y <= 0.65f) ? score * 0.1f : score;
            boolean ok = true;
            for (float[] ex : existing) {
                float dx = x - ex[0];
                float dy = y - ex[1];
                if (dx * dx + dy * dy < minDist * minDist) {
                    ok = false;
                    break;
                }
            }
            if (ok && adjusted > bestScore) {
                bestScore = adjusted;
                best = new float[]{x, y};
            }
        }
        if (best != null) {
            return best;
        }
        return new float[]{
                randRange(0.1f, 0.9f, seed),
                randRange(0.1f, 0.9f, seed + 1L)
        };
    }

    /** 对齐 Android StainNoise.seededRandom */
    private static float seededRandom(long seed) {
        long t = seed + 0x6D2B79F5L;
        t = imul32(t ^ (t >>> 15), t | 1L);
        t = t ^ (t + imul32(t ^ (t >>> 7), t | 61L));
        long u = (t ^ (t >>> 14)) & 0xFFFFFFFFL;
        return u / 4294967296f;
    }

    /** 对齐 Android StainNoise.randRange(min, max, seed) */
    private static float randRange(float min, float max, long seed) {
        return min + seededRandom(seed) * (max - min);
    }

    private static int randInt(int min, int max, long seed) {
        return (int) Math.floor(randRange(min, max + 1f, seed));
    }

    private static long imul32(long a, long b) {
        return (a * b) & 0xFFFFFFFFL;
    }
}
