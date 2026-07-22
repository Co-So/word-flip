package com.wordflip.dto.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 学习卡详情 JSON 契约测试。
 */
class LearningCardDetailResponseTest {

    /**
     * 详情响应必须与 OpenAPI 一致，学习卡字段位于根节点，不能再包在 card 对象中。
     */
    @Test
    void serializesLearningCardFieldsAtRoot() throws Exception {
        var card = new LearningCardResponse(
                11L, 22L, 33L, "apple", "apple", "/ˈæpəl/", 1, List.of()
        );
        var memory = new FsrsMemoryResponse("new", Instant.EPOCH, 0, 0, 0, 0);
        var response = LearningCardDetailResponse.from(
                card,
                new CardProgressResponse(memory, memory, 0),
                List.of()
        );

        var json = new ObjectMapper().findAndRegisterModules().readTree(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(response)
        );

        assertThat(json.path("cardId").asLong()).isEqualTo(11L);
        assertThat(json.path("lexemeId").asLong()).isEqualTo(22L);
        assertThat(json.path("bookId").asLong()).isEqualTo(33L);
        assertThat(json.has("card")).isFalse();
        assertThat(json.has("progress")).isTrue();
        assertThat(json.path("progress").path("displayHeatLevel").asInt(-1)).isZero();
        assertThat(json.has("sourceMaterials")).isTrue();
    }
}
