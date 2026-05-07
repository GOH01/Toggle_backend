package com.toggle.dto.map;

import java.util.List;

public record UserMapDetailResponse(
    UserMapSummaryResponse map,
    List<Long> stores,
    List<Long> publicInstitutions
) {
}
