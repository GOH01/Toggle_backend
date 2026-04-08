package com.toggle.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AddressNormalizer {

    public String normalize(String rawAddress) {
        if (rawAddress == null) {
            return "";
        }

        String normalized = rawAddress.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace("특별자치도", "도");
        normalized = normalized.replace("특별자치시", "시");
        normalized = normalized.replace("광역시", "시");
        normalized = normalized.replace("특별시", "시");
        normalized = normalized.replaceAll("[,()\\-]", " ");
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("\\b\\d+층\\b", "");
        normalized = normalized.replaceAll("\\b\\d+호\\b", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }
}
