package com.toggle.service;

import com.toggle.dto.publicinstitution.PublicInstitutionLookupItemResponse;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupRequest;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupResponse;
import com.toggle.entity.CongestionLevel;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.PublicInstitution;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.PublicInstitutionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicInstitutionService {

    private final PublicInstitutionRepository publicInstitutionRepository;
    private final Random random = new Random();

    public PublicInstitutionService(PublicInstitutionRepository publicInstitutionRepository) {
        this.publicInstitutionRepository = publicInstitutionRepository;
    }

    @Transactional
    public PublicInstitutionLookupResponse lookupInstitutions(PublicInstitutionLookupRequest request) {
        ExternalSource source = parseExternalSource(request.externalSource());
        List<String> externalPlaceIds = request.externalPlaceIds().stream()
            .map(String::trim)
            .filter(id -> !id.isBlank())
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));

        if (externalPlaceIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_EXTERNAL_PLACE_IDS", "조회할 외부 장소 ID가 없습니다.");
        }

        // DB에서 조회하고 없는 것은 목업용으로 자동 생성 (마이그레이션 단계)
        List<PublicInstitution> existing = publicInstitutionRepository.findAllByExternalSourceAndExternalPlaceIdIn(source, externalPlaceIds);
        
        List<String> existingIds = existing.stream().map(PublicInstitution::getExternalPlaceId).toList();
        
        externalPlaceIds.stream()
            .filter(id -> !existingIds.contains(id))
            .forEach(id -> {
                PublicInstitution pi = new PublicInstitution(source, id, "공공기관 " + id);
                // 목업 데이터와 유사한 초기 상태 부여
                CongestionLevel[] levels = CongestionLevel.values();
                pi.updateStatus(levels[random.nextInt(4)], random.nextInt(30)); 
                publicInstitutionRepository.save(pi);
                existing.add(pi);
            });

        List<PublicInstitutionLookupItemResponse> institutions = existing.stream()
            .map(pi -> new PublicInstitutionLookupItemResponse(
                pi.getId(),
                pi.getExternalSource().name(),
                pi.getExternalPlaceId(),
                pi.getName(),
                pi.getCongestionLevel().name(),
                pi.getWaitTime(),
                pi.getOperatingHours(),
                pi.getStatusUpdatedAt() == null ? null : pi.getStatusUpdatedAt().toString()
            ))
            .toList();

        return new PublicInstitutionLookupResponse(institutions);
    }

    @Transactional(readOnly = true)
    public PublicInstitution getInstitution(Long id) {
        return publicInstitutionRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "공공기관을 찾을 수 없습니다."));
    }

    private ExternalSource parseExternalSource(String source) {
        try {
            return ExternalSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXTERNAL_SOURCE", "지원하지 않는 외부 장소 소스입니다.");
        }
    }
}
