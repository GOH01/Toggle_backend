package com.toggle.dto.kakao;

import com.toggle.dto.publicinstitution.PublicInstitutionLookupItemResponse;
import com.toggle.dto.store.StoreLookupItemResponse;
import java.util.List;

public record KakaoLookupResponse(
    List<StoreLookupItemResponse> stores,
    List<PublicInstitutionLookupItemResponse> institutions
) {
}
