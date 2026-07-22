package com.wordflip.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.wordflip.dto.group.CreateCustomGroupRequest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 分组控制器的新 cardId 路径契约。
 */
class GroupControllerTest {

    @Test
    void exposesCardsAndUnassignedCardPaths() throws Exception {
        Method groupCards = GroupController.class.getMethod(
                "listGroupCards", Long.class, int.class, int.class
        );
        Method unassigned = GroupController.class.getMethod(
                "listUnassignedCards", boolean.class, String.class, int.class, int.class
        );

        assertThat(groupCards.getAnnotation(GetMapping.class).value())
                .containsExactly("/groups/{groupId}/cards");
        assertThat(unassigned.getAnnotation(GetMapping.class).value())
                .containsExactly("/learning/cards/unassigned");
        assertThat(new CreateCustomGroupRequest(List.of(10L, 11L), "重点").cardIds())
                .containsExactly(10L, 11L);
    }
}
