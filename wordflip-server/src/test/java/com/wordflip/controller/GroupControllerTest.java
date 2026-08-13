package com.wordflip.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.security.UserPrincipal;
import com.wordflip.service.GroupService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 分组控制器的新 cardId 路径契约。
 */
class GroupControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    void forwardsRawFilterAndPageOptionsToService() {
        GroupService service = mock(GroupService.class);
        GroupController controller = new GroupController(service);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(7L), null, List.of())
        );

        controller.listGroups("other", "sql");
        controller.listGroupCards(11L, 0, 101);
        controller.listUnassignedCards(true, "ab", 0, 101);

        verify(service).listGroups(7L, "other", "sql");
        verify(service).listGroupCards(7L, 11L, 0, 101);
        verify(service).listUnassignedCards(7L, true, "ab", 0, 101);
    }
}
