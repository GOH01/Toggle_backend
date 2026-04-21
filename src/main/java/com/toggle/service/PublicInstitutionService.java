package com.toggle.service;

import com.toggle.dto.publicinstitution.PublicInstitutionLookupItemResponse;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupRequest;
import com.toggle.dto.publicinstitution.PublicInstitutionLookupResponse;
import com.toggle.entity.CongestionLevel;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.PublicInstitution;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.PublicInstitutionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
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
        
        List<PublicInstitutionLookupRequest.PublicInstitutionLookupItemRequest> items = request.items();
        if (items == null || items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_REQUEST_ITEMS", "조회할 장소 정보가 없습니다.");
        }

        List<String> externalPlaceIds = items.stream()
            .map(PublicInstitutionLookupRequest.PublicInstitutionLookupItemRequest::externalPlaceId)
            .toList();

        // DB에서 기존 항목 조회
        List<PublicInstitution> existing = publicInstitutionRepository.findAllByExternalSourceAndExternalPlaceIdIn(source, externalPlaceIds);
        Map<String, PublicInstitution> existingMap = existing.stream()
            .collect(Collectors.toMap(PublicInstitution::getExternalPlaceId, pi -> pi));

        List<PublicInstitution> results = new ArrayList<>();

        for (var item : items) {
            PublicInstitution pi = existingMap.get(item.externalPlaceId());
            if (pi == null) {
                // 신규 생성 (데이터 자동 생성 및 마이그레이션)
                pi = new PublicInstitution(source, item.externalPlaceId(), item.name());
                pi.updateMetadata(
                    item.name(),
                    item.address(),
                    item.latitude() != null ? BigDecimal.valueOf(item.latitude()) : null,
                    item.longitude() != null ? BigDecimal.valueOf(item.longitude()) : null,
                    "09:00 - 18:00" // 기본 영업시간
                );
                
                // 목업 상태 부여
                CongestionLevel[] levels = CongestionLevel.values();
                pi.updateStatus(levels[random.nextInt(4)], random.nextInt(30));
                
                publicInstitutionRepository.save(pi);
            } else {
                // 기존 데이터 메타데이터 보강 (주소나 좌표가 없는 경우)
                if (pi.getAddress() == null && item.address() != null) {
                    pi.updateMetadata(
                        pi.getName(),
                        item.address(),
                        item.latitude() != null ? BigDecimal.valueOf(item.latitude()) : pi.getLatitude(),
                        item.longitude() != null ? BigDecimal.valueOf(item.longitude()) : pi.getLongitude(),
                        pi.getOperatingHours()
                    );
                }
            }
            results.add(pi);
        }

        List<PublicInstitutionLookupItemResponse> responses = results.stream()
            .map(pi -> new PublicInstitutionLookupItemResponse(
                pi.getId(),
                pi.getExternalSource().name(),
                pi.getExternalPlaceId(),
                pi.getName(),
                pi.getAddress(),
                pi.getLatitude() == null ? null : pi.getLatitude().doubleValue(),
                pi.getLongitude() == null ? null : pi.getLongitude().doubleValue(),
                pi.getCongestionLevel().name(),
                pi.getWaitTime(),
                pi.getOperatingHours(),
                pi.getStatusUpdatedAt() == null ? null : pi.getStatusUpdatedAt().toString()
            ))
            .toList();

        return new PublicInstitutionLookupResponse(responses);
    }

    @Transactional(readOnly = true)
    public PublicInstitution getInstitution(Long id) {
        return publicInstitutionRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "공공기관을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PublicInstitutionLookupResponse getInstitutionsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_INSTITUTION_IDS", "조회할 공공기관 ID가 없습니다.");
        }

        List<Long> normalizedIds = ids.stream()
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.collectingAndThen(
                Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));

        Map<Long, PublicInstitution> institutionMap = publicInstitutionRepository.findAllByIdIn(normalizedIds).stream()
            .collect(Collectors.toMap(PublicInstitution::getId, institution -> institution));

        List<PublicInstitutionLookupItemResponse> responses = normalizedIds.stream()
            .map(institutionMap::get)
            .filter(java.util.Objects::nonNull)
            .map(this::toLookupItem)
            .toList();

        return new PublicInstitutionLookupResponse(responses);
    }

    private ExternalSource parseExternalSource(String source) {
        try {
            return ExternalSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXTERNAL_SOURCE", "지원하지 않는 외부 장소 소스입니다.");
        }
    }

    private PublicInstitutionLookupItemResponse toLookupItem(PublicInstitution pi) {
        return new PublicInstitutionLookupItemResponse(
            pi.getId(),
            pi.getExternalSource().name(),
            pi.getExternalPlaceId(),
            pi.getName(),
            pi.getAddress(),
            pi.getLatitude() == null ? null : pi.getLatitude().doubleValue(),
            pi.getLongitude() == null ? null : pi.getLongitude().doubleValue(),
            pi.getCongestionLevel().name(),
            pi.getWaitTime(),
            pi.getOperatingHours(),
            pi.getStatusUpdatedAt() == null ? null : pi.getStatusUpdatedAt().toString()
        );
    }
}
