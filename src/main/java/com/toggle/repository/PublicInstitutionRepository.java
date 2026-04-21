package com.toggle.repository;

import com.toggle.entity.ExternalSource;
import com.toggle.entity.PublicInstitution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicInstitutionRepository extends JpaRepository<PublicInstitution, Long> {

    Optional<PublicInstitution> findByExternalSourceAndExternalPlaceId(ExternalSource externalSource, String externalPlaceId);

    List<PublicInstitution> findAllByExternalSourceAndExternalPlaceIdIn(ExternalSource externalSource, List<String> externalPlaceIds);

    List<PublicInstitution> findAllByIdIn(List<Long> ids);
}
