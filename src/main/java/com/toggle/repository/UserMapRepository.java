package com.toggle.repository;

import com.toggle.entity.UserMap;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMapRepository extends JpaRepository<UserMap, Long> {

    Optional<UserMap> findByIdAndDeletedAtIsNull(Long id);

    Optional<UserMap> findByIdAndOwnerUserIdAndDeletedAtIsNull(Long id, Long ownerUserId);

    Optional<UserMap> findByPublicMapUuidAndDeletedAtIsNull(String publicMapUuid);

    List<UserMap> findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(Long ownerUserId);

    Page<UserMap> findAllByIsPublicTrueAndDeletedAtIsNull(Pageable pageable);
}
