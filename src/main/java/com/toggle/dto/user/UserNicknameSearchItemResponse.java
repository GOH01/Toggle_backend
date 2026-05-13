package com.toggle.dto.user;

import java.util.List;

public record UserNicknameSearchItemResponse(
    Long userId,
    String nickname,
    String profileImageUrl,
    List<UserNicknameSearchMapResponse> maps
) {
}
