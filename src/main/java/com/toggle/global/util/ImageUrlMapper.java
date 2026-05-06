package com.toggle.global.util;

import java.net.URI;
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
                URI uri = URI.create(normalized);
                String host = uri.getHost();
                String path = uri.getPath();
                if (host != null && host.contains("amazonaws.com") && path != null && !path.isBlank()) {
                    String key = path.startsWith("/") ? path.substring(1) : path;
                    return buildFileViewPath(key);
                }
            } catch (IllegalArgumentException ex) {
                return normalized;
            }

            return normalized;
        }

        return normalized.startsWith("/") ? normalized : buildFileViewPath(normalized);
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

    private static String buildFileViewPath(String key) {
        return FILE_VIEW_PATH + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
}
