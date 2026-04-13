package com.toggle.dto.store;

import java.util.List;

public record StoreLookupResponse(
    List<StoreLookupItemResponse> stores
) {
}
