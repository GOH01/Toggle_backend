package com.toggle.dto.store;

public record StorePriceItemItemResponse(
    Long priceItemId,
    String name,
    int price,
    boolean representative,
    String description,
    String imageUrl,
    int displayOrder,
    boolean available
) {
}
