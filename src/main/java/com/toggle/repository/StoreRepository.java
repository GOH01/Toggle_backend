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

    List<Store> findAllByExternalSourceAndExternalPlaceIdIn(ExternalSource externalSource, List<String> externalPlaceIds);

    List<Store> findAllByIdIn(List<Long> ids);

    List<Store> findTop10ByAddressNormalizedContaining(String addressNormalized);

    List<Store> findAllByIsVerifiedTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :id")
    Optional<Store> findByIdForUpdate(Long id);
}
