package com.toggle.repository;

import com.toggle.entity.StorePriceItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorePriceItemRepository extends JpaRepository<StorePriceItem, Long> {

    List<StorePriceItem> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);

    void deleteAllByStoreId(Long storeId);
}
