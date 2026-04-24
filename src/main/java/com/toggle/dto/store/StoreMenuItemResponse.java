package com.toggle.dto.store;

public record StoreMenuItemResponse(
    Long menuId,
    String name,
    int price,
    boolean representative,
    String description,
    String imageUrl,
    int displayOrder,
    boolean available
) {
}
