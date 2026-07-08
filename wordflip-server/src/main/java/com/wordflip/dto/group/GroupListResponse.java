package com.wordflip.dto.group;

import java.util.List;

/**
 * 分组列表响应（GET /groups）。
 */
public record GroupListResponse(List<GroupDetail> groups) {
}
