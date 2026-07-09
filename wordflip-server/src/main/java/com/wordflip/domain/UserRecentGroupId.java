package com.wordflip.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * user_recent_groups 复合主键。
 */
public class UserRecentGroupId implements Serializable {

    private Long userId;
    private Long groupId;

    public UserRecentGroupId() {
    }

    public UserRecentGroupId(Long userId, Long groupId) {
        this.userId = userId;
        this.groupId = groupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRecentGroupId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, groupId);
    }
}
