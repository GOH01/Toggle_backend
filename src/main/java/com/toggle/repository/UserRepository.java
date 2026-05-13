package com.toggle.repository;

import com.toggle.entity.User;
import com.toggle.entity.UserStatus;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<User> findByPublicMapUuid(String publicMapUuid);

    List<User> findTop20ByPublicMapTrueAndStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(UserStatus status, String nickname);
}
