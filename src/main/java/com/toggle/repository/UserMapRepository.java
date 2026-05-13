package com.toggle.repository;

import com.toggle.entity.UserMap;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface UserMapRepository extends JpaRepository<UserMap, Long> {

    Optional<UserMap> findByIdAndDeletedAtIsNull(Long id);

    Optional<UserMap> findByIdAndOwnerUserIdAndDeletedAtIsNull(Long id, Long ownerUserId);

    Optional<UserMap> findByPublicMapUuidAndDeletedAtIsNull(String publicMapUuid);

    List<UserMap> findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(Long ownerUserId);

    List<UserMap> findAllByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long ownerUserId);

    @EntityGraph(attributePaths = "ownerUser")
    List<UserMap> findAllByOwnerUserIdInAndDeletedAtIsNullOrderByOwnerUserIdAscCreatedAtDescIdDesc(Collection<Long> ownerUserIds);

    Page<UserMap> findAllByIsPublicTrueAndDeletedAtIsNull(Pageable pageable);
}
