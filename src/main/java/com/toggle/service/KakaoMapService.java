package com.toggle.service;

import com.toggle.dto.kakao.KakaoAddressSearchResponse;
import com.toggle.dto.kakao.KakaoKeywordSearchRequest;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
import com.toggle.dto.kakao.KakaoMapVerificationResult;
import com.toggle.global.exception.KakaoAddressSearchException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KakaoMapService {

    private static final Logger log = LoggerFactory.getLogger(KakaoMapService.class);
    private static final String ADDRESS_API_PATH = "/v2/local/search/address.json";
    private static final String KEYWORD_API_PATH = "/v2/local/search/keyword.json";
    private static final int KEYWORD_SEARCH_RADIUS_METERS = 300;
    private static final int KEYWORD_SEARCH_PAGE = 1;
    private static final int KEYWORD_SEARCH_SIZE = 15;
    private static final String KEYWORD_SEARCH_SORT = "distance";
    private static final double MIN_PLACE_NAME_SIMILARITY = 0.35d;

    private final KakaoPlaceClient kakaoPlaceClient;
    private final AddressNormalizer addressNormalizer;

    public KakaoMapService(KakaoPlaceClient kakaoPlaceClient, AddressNormalizer addressNormalizer) {
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.addressNormalizer = addressNormalizer;
    }

    public KakaoMapVerificationResult verify(String storeName, String businessAddress) {
        String addressQuery = normalizeKakaoAddressQuery(businessAddress);
        log.info("Kakao address search query = {}", addressQuery);
        log.info("Kakao api path = {}", ADDRESS_API_PATH);

        try {
            KakaoAddressSearchResponse addressResponse = kakaoPlaceClient.searchByAddress(addressQuery);
            List<KakaoAddressSearchResponse.KakaoAddressDocument> addressDocuments = addressResponse.documents() == null
                ? List.of()
                : addressResponse.documents();
            log.info("Kakao address documents size = {}", addressDocuments.size());
            if (addressDocuments.isEmpty()) {
                return buildAddressFailureResult(
                    addressQuery,
                    addressResponse,
                    HttpStatus.OK,
                    "KAKAO_NO_DOCUMENTS",
                    "카카오 주소 검색 결과가 없습니다.",
                    null
                );
            }

            KakaoAddressSearchResponse.KakaoAddressDocument selectedAddressDocument = addressDocuments.get(0);
            KakaoMapVerificationResult.AddressMatch addressMatch = toAddressMatch(selectedAddressDocument, addressQuery);
            if (addressMatch.latitude() == null || addressMatch.longitude() == null) {
                return buildAddressFailureResult(
                    addressQuery,
                    addressResponse,
                    HttpStatus.OK,
                    "KAKAO_RESPONSE_PARSE_FAILED",
                    "카카오 주소 검색 응답을 해석할 수 없습니다.",
                    null
                );
            }

            String keywordQuery = blankToNull(storeName);
            if (!StringUtils.hasText(keywordQuery)) {
                return buildManualReviewResult(
                    addressQuery,
                    addressResponse,
                    addressMatch,
                    null,
                    null,
                    null,
                    null,
                    0,
                    "MANUAL_REVIEW_REQUIRED",
                    "매장명이 없어 수동 검토가 필요합니다."
                );
            }

            double keywordLatitude = addressMatch.latitude().doubleValue();
            double keywordLongitude = addressMatch.longitude().doubleValue();
            log.info(
                "Kakao keyword search query = {}, x = {}, y = {}, radius = {}",
                keywordQuery,
                keywordLongitude,
                keywordLatitude,
                KEYWORD_SEARCH_RADIUS_METERS
            );

            try {
                KakaoPlaceSearchResponse keywordResponse = kakaoPlaceClient.searchKeyword(new KakaoKeywordSearchRequest(
                    keywordQuery,
                    null,
                    keywordLatitude,
                    keywordLongitude,
                    KEYWORD_SEARCH_RADIUS_METERS,
                    KEYWORD_SEARCH_PAGE,
                    KEYWORD_SEARCH_SIZE,
                    KEYWORD_SEARCH_SORT
                ));
                List<KakaoPlaceSearchResponse.KakaoPlaceDocument> keywordDocuments = keywordResponse.documents() == null
                    ? List.of()
                    : keywordResponse.documents();
                log.info("Kakao keyword search candidates size = {}", keywordDocuments.size());
                List<KakaoKeywordCandidate> matchedCandidates = rankKeywordCandidates(
                    addressQuery,
                    keywordQuery,
                    keywordDocuments,
                    keywordLatitude,
                    keywordLongitude
                );
                log.info("Kakao keyword matched candidates size = {}", matchedCandidates.size());
                if (matchedCandidates.isEmpty()) {
                    return buildManualReviewResult(
                        addressQuery,
                        addressResponse,
                        addressMatch,
                        keywordQuery,
                        keywordResponse,
                        keywordLatitude,
                        keywordLongitude,
                        matchedCandidates.size(),
                        "KAKAO_NO_MATCHED_PLACE",
                        "카카오 키워드 검색 결과가 없어 수동 검토가 필요합니다."
                    );
                }

                KakaoKeywordCandidate selectedCandidate = matchedCandidates.get(0);
                log.info("Matched kakao place = {}", selectedCandidate.document().place_name());
                log.info("Matched distance = {}m", Math.round(selectedCandidate.distanceMeters()));
                log.info("Matched similarity score = {}", selectedCandidate.similarity());

                KakaoMapVerificationResult.KeywordMatch selectedMatch = toKeywordMatch(selectedCandidate);
                return new KakaoMapVerificationResult(
                    KakaoMapVerificationResult.Outcome.SUCCESS,
                    addressQuery,
                    ADDRESS_API_PATH,
                    addressResponse,
                    addressMatch,
                    null,
                    keywordQuery,
                    KEYWORD_API_PATH,
                    keywordLatitude,
                    keywordLongitude,
                    KEYWORD_SEARCH_RADIUS_METERS,
                    keywordResponse,
                    matchedCandidates.size(),
                    selectedMatch,
                    null,
                    null,
                    null
                );
            } catch (Exception ex) {
                log.warn("Kakao keyword search failed query = {}, path = {}, reason = {}", keywordQuery, KEYWORD_API_PATH, ex.getMessage(), ex);
                return buildManualReviewResult(
                    addressQuery,
                    addressResponse,
                    addressMatch,
                    keywordQuery,
                    null,
                    keywordLatitude,
                    keywordLongitude,
                    0,
                    "KAKAO_KEYWORD_SEARCH_ERROR",
                    "카카오 키워드 검색에 실패하여 수동 검토가 필요합니다."
                );
            }
        } catch (KakaoAddressSearchException ex) {
            return buildAddressFailureResult(
                addressQuery,
                null,
                ex.getStatus(),
                ex.getReasonCode(),
                buildAddressFailureMessage(ex.getReasonCode()),
                ex.getResponseBody()
            );
        } catch (Exception ex) {
            return buildAddressFailureResult(
                addressQuery,
                null,
                HttpStatus.SERVICE_UNAVAILABLE,
                "KAKAO_RESPONSE_PARSE_FAILED",
                "카카오 주소 검색 응답을 해석할 수 없습니다.",
                null
            );
        }
    }

    private KakaoMapVerificationResult buildAddressFailureResult(
        String addressQuery,
        KakaoAddressSearchResponse addressResponse,
        HttpStatus failureStatus,
        String failureCode,
        String failureMessage,
        String failureResponseBody
    ) {
        return new KakaoMapVerificationResult(
            KakaoMapVerificationResult.Outcome.ADDRESS_FAILURE,
            addressQuery,
            ADDRESS_API_PATH,
            addressResponse,
            null,
            failureStatus,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            failureCode,
            failureMessage,
            failureResponseBody
        );
    }

    private KakaoMapVerificationResult buildManualReviewResult(
        String addressQuery,
        KakaoAddressSearchResponse addressResponse,
        KakaoMapVerificationResult.AddressMatch addressMatch,
        String keywordQuery,
        KakaoPlaceSearchResponse keywordResponse,
        Double keywordLatitude,
        Double keywordLongitude,
        int candidateCount,
        String failureCode,
        String failureMessage
    ) {
        return new KakaoMapVerificationResult(
            KakaoMapVerificationResult.Outcome.MANUAL_REVIEW_REQUIRED,
            addressQuery,
            ADDRESS_API_PATH,
            addressResponse,
            addressMatch,
            null,
            keywordQuery,
            KEYWORD_API_PATH,
            keywordLatitude,
            keywordLongitude,
            keywordLatitude == null || keywordLongitude == null ? null : KEYWORD_SEARCH_RADIUS_METERS,
            keywordResponse,
            candidateCount,
            null,
            failureCode,
            failureMessage,
            null
        );
    }

    private KakaoMapVerificationResult.AddressMatch toAddressMatch(
        KakaoAddressSearchResponse.KakaoAddressDocument document,
        String addressQuery
    ) {
        return new KakaoMapVerificationResult.AddressMatch(
            blankToNull(document.id()),
            firstNonBlank(document.place_name(), addressQuery),
            blankToNull(document.road_address_name()),
            blankToNull(document.address_name()),
            blankToNull(document.category_name()),
            document.y(),
            document.x()
        );
    }

    private KakaoMapVerificationResult.KeywordMatch toKeywordMatch(KakaoKeywordCandidate candidate) {
        KakaoPlaceSearchResponse.KakaoPlaceDocument document = candidate.document();
        return new KakaoMapVerificationResult.KeywordMatch(
            blankToNull(document.id()),
            blankToNull(document.place_name()),
            blankToNull(document.road_address_name()),
            blankToNull(document.address_name()),
            blankToNull(document.phone()),
            blankToNull(document.category_name()),
            candidate.latitude(),
            candidate.longitude(),
            candidate.similarity(),
            candidate.distanceMeters()
        );
    }

    private List<KakaoKeywordCandidate> rankKeywordCandidates(
        String addressQuery,
        String keywordQuery,
        List<KakaoPlaceSearchResponse.KakaoPlaceDocument> documents,
        double baseLatitude,
        double baseLongitude
    ) {
        String normalizedKeywordQuery = normalizeKakaoPlaceName(keywordQuery);
        String normalizedAddressQuery = normalizeAddressForComparison(addressQuery);
        return documents.stream()
            .map(document -> toKeywordCandidate(document, normalizedKeywordQuery, normalizedAddressQuery, baseLatitude, baseLongitude))
            .filter(java.util.Objects::nonNull)
            .filter(candidate -> candidate.similarity() >= MIN_PLACE_NAME_SIMILARITY)
            .sorted(Comparator.comparingDouble(KakaoKeywordCandidate::similarity).reversed()
                .thenComparingDouble(KakaoKeywordCandidate::distanceMeters)
                .thenComparing(Comparator.comparingDouble(KakaoKeywordCandidate::addressSimilarity).reversed()))
            .toList();
    }

    private KakaoKeywordCandidate toKeywordCandidate(
        KakaoPlaceSearchResponse.KakaoPlaceDocument document,
        String normalizedKeywordQuery,
        String normalizedAddressQuery,
        double baseLatitude,
        double baseLongitude
    ) {
        BigDecimal latitude = parseCoordinate(document.y());
        BigDecimal longitude = parseCoordinate(document.x());
        if (latitude == null || longitude == null) {
            return null;
        }
        double similarity = calculatePlaceNameSimilarity(normalizedKeywordQuery, normalizeKakaoPlaceName(document.place_name()));
        double addressSimilarity = calculatePlaceNameSimilarity(
            normalizedAddressQuery,
            normalizeAddressForComparison(firstNonBlank(document.road_address_name(), document.address_name()))
        );
        double distanceMeters = calculateDistanceMeters(
            baseLatitude,
            baseLongitude,
            latitude.doubleValue(),
            longitude.doubleValue()
        );
        return new KakaoKeywordCandidate(document, latitude, longitude, similarity, addressSimilarity, distanceMeters);
    }

    private double calculatePlaceNameSimilarity(String normalizedQuery, String normalizedCandidate) {
        if (!StringUtils.hasText(normalizedQuery) || !StringUtils.hasText(normalizedCandidate)) {
            return 0.0d;
        }
        if (normalizedQuery.equals(normalizedCandidate)) {
            return 1.0d;
        }
        if (normalizedQuery.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedQuery)) {
            return 0.95d;
        }

        return Math.max(
            calculateLevenshteinSimilarity(normalizedQuery, normalizedCandidate),
            calculateTokenOverlapSimilarity(normalizedQuery, normalizedCandidate)
        );
    }

    private double calculateLevenshteinSimilarity(String left, String right) {
        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0 || rightLength == 0) {
            return 0.0d;
        }

        int[] previous = new int[rightLength + 1];
        int[] current = new int[rightLength + 1];
        for (int j = 0; j <= rightLength; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= leftLength; i++) {
            current[0] = i;
            for (int j = 1; j <= rightLength; j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        int distance = previous[rightLength];
        return 1.0d - ((double) distance / Math.max(leftLength, rightLength));
    }

    private double calculateTokenOverlapSimilarity(String normalizedQuery, String normalizedCandidate) {
        List<String> queryTokens = splitNormalizedTokens(normalizedQuery);
        List<String> candidateTokens = splitNormalizedTokens(normalizedCandidate);
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) {
            return 0.0d;
        }

        long overlap = queryTokens.stream()
            .filter(candidateTokens::contains)
            .distinct()
            .count();
        long union = queryTokens.stream().distinct().count() + candidateTokens.stream().distinct().count() - overlap;
        if (union <= 0L) {
            return 0.0d;
        }

        return (double) overlap / union;
    }

    private List<String> splitNormalizedTokens(String normalizedValue) {
        if (!StringUtils.hasText(normalizedValue)) {
            return List.of();
        }
        return java.util.Arrays.stream(normalizedValue.split("\\s+"))
            .filter(token -> !token.isBlank())
            .toList();
    }

    private double calculateDistanceMeters(
        double fromLatitude,
        double fromLongitude,
        double toLatitude,
        double toLongitude
    ) {
        double earthRadiusMeters = 6_371_000d;
        double latDistance = Math.toRadians(toLatitude - fromLatitude);
        double lngDistance = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
            * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    private BigDecimal parseCoordinate(String coordinate) {
        if (coordinate == null || coordinate.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(coordinate);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeKakaoAddressQuery(String rawAddress) {
        if (rawAddress == null) {
            return "";
        }
        return rawAddress.trim().replaceAll("\\s+", " ");
    }

    private String normalizeKakaoPlaceName(String rawPlaceName) {
        if (rawPlaceName == null) {
            return "";
        }

        String normalized = rawPlaceName.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^0-9a-zA-Z가-힣]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        normalized = normalized.replaceAll("(본점|직영점|지점|점)$", "");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String normalizeAddressForComparison(String rawAddress) {
        if (rawAddress == null) {
            return "";
        }
        return addressNormalizer.normalize(rawAddress);
    }

    private String buildAddressFailureMessage(String reasonCode) {
        if (reasonCode == null) {
            return "카카오 주소 검색에 실패했습니다.";
        }

        return switch (reasonCode) {
            case "KAKAO_NO_DOCUMENTS" -> "카카오 주소 검색 결과가 없습니다.";
            case "KAKAO_BAD_REQUEST" -> "카카오 주소 검색 요청이 올바르지 않습니다.";
            case "KAKAO_TOO_MANY_REQUESTS" -> "카카오 주소 검색 요청이 너무 많습니다.";
            case "KAKAO_RESPONSE_PARSE_FAILED" -> "카카오 주소 검색 응답을 해석할 수 없습니다.";
            default -> "카카오 주소 검색에 실패했습니다.";
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record KakaoKeywordCandidate(
        KakaoPlaceSearchResponse.KakaoPlaceDocument document,
        BigDecimal latitude,
        BigDecimal longitude,
        double similarity,
        double addressSimilarity,
        double distanceMeters
    ) {
    }
}
