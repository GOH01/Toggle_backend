package com.toggle.dto.map;

public record MapLikeResponse(
    Long mapId,
    long likeCount,
    boolean likedByMe
) {
}
