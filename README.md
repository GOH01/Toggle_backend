# Toggle Backend

Standalone Spring Boot backend for Toggle.

## Run

```bash
./gradlew test
./gradlew build
./gradlew bootRun
```

## Configuration

- `src/main/resources/application.yml` uses safe placeholder values and is ignored for local edits.
- Replace placeholders locally if you need real Kakao, AWS S3, or National Tax integration.
- Tests use `src/test/resources/application-test.yml`.
