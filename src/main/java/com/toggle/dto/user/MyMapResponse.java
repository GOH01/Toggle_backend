package com.toggle.dto.user;

import com.toggle.dto.auth.MeResponse;
import java.util.List;

public record MyMapResponse(
    MeResponse.MapProfile mapProfile,
    List<Long> stores,
    List<Long> publics
) {
}
