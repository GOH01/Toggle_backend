# Kakao Map Verification Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Kakao map verification work reliably in build/prod by resolving Kakao settings from the runtime config actually present, surfacing misconfiguration early, and adding logs that prove which values were used.

**Architecture:** Introduce a small Kakao settings resolver in backend config that reads the canonical `app.kakao.*` keys and falls back to legacy `kakao.*` keys when needed. Inject that resolved settings object into the Kakao RestTemplate config and Kakao place client so the Authorization header and base URL are built from one source of truth. Add targeted logging for active profiles, resolved config source, api-key presence, request URL, response status/body, and returned document counts.

**Tech Stack:** Java 21, Spring Boot 3.3, JUnit 5, Mockito, Spring Test, RestTemplate

---

### Task 1: Add a Kakao settings resolver with fallback and validation

**Files:**
- Create: `src/main/java/com/toggle/global/config/KakaoClientProperties.java`
- Create: `src/main/java/com/toggle/global/config/KakaoClientPropertiesConfig.java`
- Test: `src/test/java/com/toggle/global/config/KakaoClientPropertiesConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void resolvesCanonicalAppKakaoKeysWhenPresent() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("app.kakao.api-key", "app-key")
        .withProperty("app.kakao.base-url", "https://dapi.kakao.com");

    KakaoClientProperties properties = KakaoClientProperties.resolve(environment);

    assertThat(properties.apiKey()).isEqualTo("app-key");
    assertThat(properties.baseUrl()).isEqualTo("https://dapi.kakao.com");
    assertThat(properties.sourcePrefix()).isEqualTo("app.kakao");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.toggle.global.config.KakaoClientPropertiesConfigTest`
Expected: FAIL because the resolver class does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
public record KakaoClientProperties(String apiKey, String baseUrl, String sourcePrefix) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String maskedApiKey() {
        if (!hasApiKey()) {
            return "<missing>";
        }
        return apiKey.length() <= 4 ? "****" : apiKey.substring(0, 2) + "****" + apiKey.substring(apiKey.length() - 2);
    }

    public static KakaoClientProperties resolve(Environment environment) {
        // Prefer app.kakao.*, then fall back to kakao.*.
        // Throw IllegalStateException when api-key or base-url is missing after fallback.
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.toggle.global.config.KakaoClientPropertiesConfigTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/toggle/global/config/KakaoClientProperties.java src/main/java/com/toggle/global/config/KakaoClientPropertiesConfig.java src/test/java/com/toggle/global/config/KakaoClientPropertiesConfigTest.java
git commit -m "fix: resolve kakao config from canonical or legacy keys"
```

### Task 2: Wire Kakao RestTemplate and client through resolved settings

**Files:**
- Modify: `src/main/java/com/toggle/global/config/KakaoClientConfig.java`
- Modify: `src/main/java/com/toggle/service/KakaoPlaceClient.java`
- Test: `src/test/java/com/toggle/global/config/KakaoClientConfigTest.java`
- Test: `src/test/java/com/toggle/service/KakaoPlaceClientTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void kakaoRestTemplateAddsAuthorizationHeaderFromResolvedSettings() {
    RestTemplateBuilder builder = new RestTemplateBuilder();
    KakaoClientProperties properties = new KakaoClientProperties("test-kakao-key", "https://dapi.kakao.com", "kakao");

    RestTemplate restTemplate = new KakaoClientConfig().kakaoRestTemplate(builder, properties);

    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://dapi.kakao.com/v2/local/search/address.json?query=%EC%8B%9C%ED%97%98"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-kakao-key"))
        .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

    restTemplate.getForObject("/v2/local/search/address.json?query=%EC%8B%9C%ED%97%98", String.class);
    server.verify();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.toggle.global.config.KakaoClientConfigTest`
Expected: FAIL until KakaoClientConfig accepts the resolved settings bean.

- [ ] **Step 3: Write minimal implementation**

```java
@Bean
@Qualifier("kakaoRestTemplate")
RestTemplate kakaoRestTemplate(RestTemplateBuilder builder, KakaoClientProperties properties) {
    return builder
        .rootUri(properties.baseUrl())
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.apiKey())
        .build();
}
```

Update `KakaoPlaceClient` constructor to accept the resolved settings object instead of `@Value` injection, and log:
- active profiles
- resolved source prefix
- base URL
- api-key presence
- request URL
- response status/body for address lookup
- document counts for address and keyword lookups

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.toggle.global.config.KakaoClientConfigTest --tests com.toggle.service.KakaoPlaceClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/toggle/global/config/KakaoClientConfig.java src/main/java/com/toggle/service/KakaoPlaceClient.java src/test/java/com/toggle/global/config/KakaoClientConfigTest.java src/test/java/com/toggle/service/KakaoPlaceClientTest.java
git commit -m "fix: route kakao client through resolved runtime settings"
```

### Task 3: Validate production-shaped behavior and document the fix

**Files:**
- Modify: `src/test/resources/application-test.yml` if needed for any new test fixture
- Modify: `README.md` or `docs/` only if operational guidance needs updating
- Test: `./gradlew test`
- Test: `./gradlew build`

- [ ] **Step 1: Run the focused tests for Kakao flows**

Run: `./gradlew test --tests com.toggle.service.KakaoPlaceClientTest --tests com.toggle.service.KakaoMapServiceTest --tests com.toggle.global.config.KakaoClientPropertiesConfigTest --tests com.toggle.global.config.KakaoClientConfigTest`
Expected: PASS.

- [ ] **Step 2: Run the full backend test suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 3: Run the backend build**

Run: `./gradlew build`
Expected: PASS.

- [ ] **Step 4: Summarize the root cause and operating guidance**

Document:
- what config key(s) were actually being used
- why local passed but build/prod failed
- how the resolver prevents a repeat
- which key structure is canonical going forward

- [ ] **Step 5: Commit**

```bash
git add README.md docs src/test src/main/java
git commit -m "fix: harden kakao verification configuration"
```
