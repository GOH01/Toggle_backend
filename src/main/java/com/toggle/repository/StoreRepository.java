package com.toggle.repository;

import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByExternalSourceAndExternalPlaceId(ExternalSource externalSource, String externalPlaceId);
}
