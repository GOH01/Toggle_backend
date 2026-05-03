package com.toggle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.kakao.KakaoLookupRequest;
import com.toggle.dto.kakao.KakaoLookupResponse;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
import com.toggle.global.exception.GlobalExceptionHandler;
import com.toggle.global.security.CustomUserDetailsService;
import com.toggle.global.security.JwtTokenProvider;
import com.toggle.service.MobileSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileSearchController.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MobileSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MobileSearchService mobileSearchService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void searchKeywordShouldExposeProxyResponseShape() throws Exception {
        when(mobileSearchService.searchKeyword(any())).thenReturn(new KakaoPlaceSearchResponse(
            new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(1, 1, true, null),
            List.of(new KakaoPlaceSearchResponse.KakaoPlaceDocument(
                "1",
                "테스트 카페",
                "음식점 > 카페",
                "CE7",
                "카페",
                "02-123-4567",
                "서울특별시 강남구 테헤란로 1",
                "서울특별시 강남구 테헤란로 1",
                "127.0",
                "37.0",
                "http://place.map.kakao.com/1",
                "10"
            ))
        ));

        mockMvc.perform(get("/api/v1/mobile-search/places/keyword")
                .param("query", "테스트 카페")
                .param("categoryGroupCode", "CE7")
                .param("latitude", "37.0")
                .param("longitude", "127.0")
                .param("radiusMeters", "2000")
                .param("size", "15")
                .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.meta.total_count").value(1))
            .andExpect(jsonPath("$.data.documents[0].place_name").value("테스트 카페"));
    }

    @Test
    void lookupShouldValidateAndReturnCombinedResponse() throws Exception {
        when(mobileSearchService.lookup(any())).thenReturn(new KakaoLookupResponse(List.of(), List.of()));

        mockMvc.perform(post("/api/v1/mobile-search/places/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new KakaoLookupRequest(List.of(
                    new KakaoLookupRequest.KakaoLookupItemRequest("1", "테스트", "서울", 37.0, 127.0, "카페")
                )))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores").isArray())
            .andExpect(jsonPath("$.data.institutions").isArray());
    }
}
