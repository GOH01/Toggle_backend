package com.toggle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ToggleBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = userRepository.save(new User(
            "tester@toggle.com",
            "encoded-password",
            "tester",
            UserRole.USER,
            UserStatus.ACTIVE
        ));
        userId = user.getId();
    }

    @Test
    void healthEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    void resolveAndFavoriteFlowShouldWork() throws Exception {
        ResolveStoreRequest request = new ResolveStoreRequest(
            ExternalSource.KAKAO.name(),
            "1234567890",
            "맛있는 덮밥집",
            "서울시 강남구 테헤란로 123",
            "02-1234-5678",
            new BigDecimal("37.4980950"),
            new BigDecimal("127.0276100")
        );

        String response = mockMvc.perform(post("/api/v1/stores/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.externalSource").value("KAKAO"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long storeId = objectMapper.readTree(response).path("data").path("storeId").asLong();

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", storeId)
                .header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(get("/api/v1/favorites/stores")
                .header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].storeId").value(storeId));

        mockMvc.perform(delete("/api/v1/favorites/stores/{storeId}", storeId)
                .header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorited").value(false));
    }
}
