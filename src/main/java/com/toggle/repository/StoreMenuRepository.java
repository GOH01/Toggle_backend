package com.toggle.repository;

import com.toggle.entity.StoreMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMenuRepository extends JpaRepository<StoreMenu, Long> {

    List<StoreMenu> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);

    void deleteAllByStoreId(Long storeId);
}
