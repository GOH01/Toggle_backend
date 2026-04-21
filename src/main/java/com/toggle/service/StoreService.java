package com.toggle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.dto.store.ResolveStoreResponse;
import com.toggle.dto.store.StoreLookupItemResponse;
import com.toggle.dto.store.StoreLookupRequest;
import com.toggle.dto.store.StoreLookupResponse;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.LiveStatusSource;
import com.toggle.entity.Store;
import com.toggle.global.exception.ApiException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.toggle.repository.StoreRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final AddressNormalizer addressNormalizer;
    private final ObjectMapper objectMapper;

    public StoreService(StoreRepository storeRepository, AddressNormalizer addressNormalizer, ObjectMapper objectMapper) {
        this.storeRepository = storeRepository;
        this.addressNormalizer = addressNormalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResolveStoreResponse resolveStore(ResolveStoreRequest request) {
        ExternalSource source = parseExternalSource(request.externalSource());
        String normalizedExternalPlaceId = normalizeExternalPlaceId(request.externalPlaceId());
        String normalizedName = request.name().trim();
        String normalizedAddress = request.address().trim();
        String normalizedAddressKey = addressNormalizer.normalize(normalizedAddress);
        String normalizedPhone = request.phone() == null ? null : request.phone().trim();

        Store store = storeRepository.findByExternalSourceAndExternalPlaceId(source, normalizedExternalPlaceId)
            .map(existingStore -> {
                existingStore.syncResolvedPlace(
                    normalizedName,
                    normalizedPhone,
                    normalizedAddress,
                    normalizedAddressKey,
                    request.latitude(),
                    request.longitude()
                );
                return existingStore;
            })
            .orElseGet(() -> storeRepository.save(new Store(
                source,
                normalizedExternalPlaceId,
                normalizedName,
                normalizedPhone,
                normalizedAddress,
                normalizedAddressKey,
                request.latitude(),
                request.longitude()
            )));

        return new ResolveStoreResponse(store.getId(), store.getExternalSource().name(), store.getExternalPlaceId(), true);
    }

    @Transactional(readOnly = true)
    public StoreLookupResponse lookupStores(StoreLookupRequest request) {
        ExternalSource source = parseExternalSource(request.externalSource());
        List<String> externalPlaceIds = request.externalPlaceIds().stream()
            .map(this::normalizeExternalPlaceId)
            .filter(id -> !id.isBlank())
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));

        if (externalPlaceIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_EXTERNAL_PLACE_IDS", "조회할 외부 장소 ID가 없습니다.");
        }

        List<StoreLookupItemResponse> stores = storeRepository.findAllByExternalSourceAndExternalPlaceIdIn(source, externalPlaceIds)
            .stream()
            .filter(Store::isVerified)
            .map(store -> new StoreLookupItemResponse(
                store.getId(),
                store.getExternalSource().name(),
                store.getExternalPlaceId(),
                store.getName(),
                store.getCategoryName(),
                store.getAddress(),
                store.getRoadAddress(),
                store.getJibunAddress(),
                store.getPhone(),
                store.getLatitude() == null ? null : store.getLatitude().doubleValue(),
                store.getLongitude() == null ? null : store.getLongitude().doubleValue(),
                store.getBusinessStatus().name(),
                store.getLiveBusinessStatus().name(),
                store.getLiveStatusSource() == null ? null : store.getLiveStatusSource().name(),
                store.isVerified(),
                store.getVerifiedAt() == null ? null : store.getVerifiedAt().toString(),
                store.getOwnerNotice(),
                store.getOperatingOpenTime(),
                store.getOperatingCloseTime(),
                store.getBreakStartTime(),
                store.getBreakEndTime(),
                store.getRating() == null ? null : store.getRating().doubleValue(),
                deserializeImageUrls(store.getOwnerImageUrlsJson())
            ))
            .toList();

        return new StoreLookupResponse(stores);
    }

    @Transactional(readOnly = true)
    public Store getStore(Long storeId) {
        return storeRepository.findById(storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public StoreLookupResponse getStoresByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_STORE_IDS", "조회할 매장 ID가 없습니다.");
        }

        List<Long> normalizedIds = ids.stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));

        Map<Long, Store> storeMap = storeRepository.findAllByIdIn(normalizedIds).stream()
            .collect(java.util.stream.Collectors.toMap(Store::getId, store -> store));

        List<StoreLookupItemResponse> stores = normalizedIds.stream()
            .map(storeMap::get)
            .filter(java.util.Objects::nonNull)
            .map(this::toLookupItem)
            .toList();

        return new StoreLookupResponse(stores);
    }

    @Transactional(readOnly = true)
    public StoreLookupResponse getNearbyVerifiedStores(
        double latitude,
        double longitude,
        int radiusMeters,
        int limit
    ) {
        if (radiusMeters <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RADIUS", "반경은 0보다 커야 합니다.");
        }
        if (limit <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "조회 개수는 0보다 커야 합니다.");
        }

        List<StoreLookupItemResponse> stores = storeRepository.findAllByIsVerifiedTrueAndLatitudeIsNotNullAndLongitudeIsNotNull()
            .stream()
            .map(store -> new NearbyStoreCandidate(store, calculateDistanceMeters(
                latitude,
                longitude,
                store.getLatitude().doubleValue(),
                store.getLongitude().doubleValue()
            )))
            .filter(candidate -> candidate.distanceMeters() <= radiusMeters)
            .sorted(Comparator.comparingDouble(NearbyStoreCandidate::distanceMeters))
            .limit(limit)
            .map(candidate -> toLookupItem(candidate.store()))
            .toList();

        return new StoreLookupResponse(stores);
    }

    @Transactional
    public void updateStoreLiveStatus(Store store, com.toggle.entity.BusinessStatus status, LiveStatusSource source) {
        store.updateLiveBusinessStatus(status, source);
    }

    private ExternalSource parseExternalSource(String source) {
        try {
            return ExternalSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXTERNAL_SOURCE", "지원하지 않는 외부 장소 소스입니다.");
        }
    }

    private String normalizeExternalPlaceId(String externalPlaceId) {
        return externalPlaceId.trim();
    }

    private StoreLookupItemResponse toLookupItem(Store store) {
        return new StoreLookupItemResponse(
            store.getId(),
            store.getExternalSource().name(),
            store.getExternalPlaceId(),
            store.getName(),
            store.getCategoryName(),
            store.getAddress(),
            store.getRoadAddress(),
            store.getJibunAddress(),
            store.getPhone(),
            store.getLatitude() == null ? null : store.getLatitude().doubleValue(),
            store.getLongitude() == null ? null : store.getLongitude().doubleValue(),
            store.getBusinessStatus().name(),
            store.getLiveBusinessStatus().name(),
            store.getLiveStatusSource() == null ? null : store.getLiveStatusSource().name(),
            store.isVerified(),
            store.getVerifiedAt() == null ? null : store.getVerifiedAt().toString(),
            store.getOwnerNotice(),
            store.getOperatingOpenTime(),
            store.getOperatingCloseTime(),
            store.getBreakStartTime(),
            store.getBreakEndTime(),
            store.getRating() == null ? null : store.getRating().doubleValue(),
            deserializeImageUrls(store.getOwnerImageUrlsJson())
        );
    }

    private List<String> deserializeImageUrls(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(
                rawJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private double calculateDistanceMeters(
        double fromLatitude,
        double fromLongitude,
        double toLatitude,
        double toLongitude
    ) {
        double earthRadiusMeters = 6_371_000d;
        double latDistance = Math.toRadians(toLatitude - fromLatitude);
        double lngDistance = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
            * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    private record NearbyStoreCandidate(Store store, double distanceMeters) {
    }
}
