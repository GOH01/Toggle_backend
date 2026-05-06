package com.toggle.global.util;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ImageUrlMapper {

    private static final String FILE_VIEW_PATH = "/api/v1/files/view?key=";

    private ImageUrlMapper() {
    }

    public static String toBrowserUrl(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim();
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.startsWith(FILE_VIEW_PATH)) {
            return normalized;
        }

        if (normalized.startsWith("api/v1/files/view?key=")) {
            return "/" + normalized;
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                String key = toObjectKey(URI.create(normalized));
                if (key != null) {
                    return buildFileViewPath(key);
                }
            } catch (IllegalArgumentException ex) {
                return normalized;
            }

            return normalized;
        }

        String objectKey = toObjectKey(normalized);
        return objectKey == null ? normalized : buildFileViewPath(objectKey);
    }

    public static List<String> toBrowserUrls(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        return rawValues.stream()
            .map(ImageUrlMapper::toBrowserUrl)
            .filter(value -> value != null && !value.isBlank())
            .toList();
    }

    public static String toObjectKey(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim();
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.startsWith(FILE_VIEW_PATH)) {
            return decodeObjectKey(normalized.substring(FILE_VIEW_PATH.length()));
        }

        if (normalized.startsWith("api/v1/files/view?key=")) {
            return decodeObjectKey(normalized.substring("api/v1/files/view?key=".length()));
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                return toObjectKey(URI.create(normalized));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    public static List<String> toObjectKeys(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        return rawValues.stream()
            .map(ImageUrlMapper::toObjectKey)
            .filter(value -> value != null && !value.isBlank())
            .toList();
    }

    private static String buildFileViewPath(String key) {
        return FILE_VIEW_PATH + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    private static String toObjectKey(URI uri) {
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || !host.contains("amazonaws.com") || path == null || path.isBlank()) {
            return null;
        }

        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        if (normalizedPath.isBlank()) {
            return null;
        }

        return decodePath(normalizedPath);
    }

    private static String decodeObjectKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }

        return URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
    }

    private static String decodePath(String encodedPath) {
        return List.of(encodedPath.split("/"))
            .stream()
            .map(segment -> URLDecoder.decode(segment, StandardCharsets.UTF_8))
            .reduce((left, right) -> left + "/" + right)
            .orElse(null);
    }
}
