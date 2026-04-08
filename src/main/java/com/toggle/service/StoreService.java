package com.toggle.service;

import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.dto.store.ResolveStoreResponse;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.LiveStatusSource;
import com.toggle.entity.Store;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.StoreRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final AddressNormalizer addressNormalizer;

    public StoreService(StoreRepository storeRepository, AddressNormalizer addressNormalizer) {
        this.storeRepository = storeRepository;
        this.addressNormalizer = addressNormalizer;
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
}
