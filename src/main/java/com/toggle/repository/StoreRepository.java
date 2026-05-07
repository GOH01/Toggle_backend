package com.toggle.repository;

import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByExternalSourceAndExternalPlaceId(ExternalSource externalSource, String externalPlaceId);

    Optional<Store> findByExternalSourceAndExternalPlaceIdAndDeletedAtIsNull(ExternalSource externalSource, String externalPlaceId);

    List<Store> findAllByExternalSourceAndExternalPlaceIdInAndDeletedAtIsNull(ExternalSource externalSource, List<String> externalPlaceIds);

    List<Store> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    Optional<Store> findByIdAndDeletedAtIsNull(Long id);

    List<Store> findAllByDeletedAtIsNullOrderByIdDesc();

    List<Store> findTop10ByAddressNormalizedContainingAndDeletedAtIsNull(String addressNormalized);

    List<Store> findAllByIsVerifiedTrueAndLatitudeIsNotNullAndLongitudeIsNotNullAndDeletedAtIsNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :id and s.deletedAt is null")
    Optional<Store> findByIdForUpdate(Long id);
}
