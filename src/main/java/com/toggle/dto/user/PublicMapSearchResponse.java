package com.toggle.dto.user;

import java.util.List;

public record PublicMapSearchResponse(
    List<PublicMapSearchItemResponse> content
) {
}
