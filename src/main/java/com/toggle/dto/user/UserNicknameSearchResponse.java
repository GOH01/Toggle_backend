package com.toggle.dto.user;

import java.util.List;

public record UserNicknameSearchResponse(
    String nickname,
    List<UserNicknameSearchItemResponse> users
) {
}
