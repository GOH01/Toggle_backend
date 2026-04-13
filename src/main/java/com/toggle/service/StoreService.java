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
import java.util.LinkedHashSet;
import java.util.List;
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
}
