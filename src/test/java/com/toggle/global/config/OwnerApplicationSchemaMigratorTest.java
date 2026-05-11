package com.toggle.global.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.stubbing.Answer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class OwnerApplicationSchemaMigratorTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void run_altersMapVerificationEnumColumnsWhenOutdated() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any()))
            .thenAnswer(enumColumnTypeAnswer(
                "enum('ADDRESS_ONLY','NAME_AND_ADDRESS')",
                "enum('FAILED','SUCCESS')"
            ));

        new OwnerApplicationSchemaMigrator(jdbcTemplate).run();

        verify(jdbcTemplate).execute("ALTER TABLE map_verification_histories MODIFY COLUMN query_type enum('NAME_AND_ADDRESS','ADDRESS_ONLY','ADDRESS_SEARCH','KEYWORD_SEARCH') NOT NULL");
        verify(jdbcTemplate).execute("ALTER TABLE map_verification_histories MODIFY COLUMN status enum('FAILED','SUCCESS','MANUAL_REVIEW_REQUIRED') NOT NULL");
    }

    @Test
    void run_skipsMapVerificationEnumColumnsWhenUpToDate() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any()))
            .thenAnswer(enumColumnTypeAnswer(
                "enum('NAME_AND_ADDRESS','ADDRESS_ONLY','ADDRESS_SEARCH','KEYWORD_SEARCH')",
                "enum('FAILED','SUCCESS','MANUAL_REVIEW_REQUIRED')"
            ));

        new OwnerApplicationSchemaMigrator(jdbcTemplate).run();

        verify(jdbcTemplate, never()).execute("ALTER TABLE map_verification_histories MODIFY COLUMN query_type enum('NAME_AND_ADDRESS','ADDRESS_ONLY','ADDRESS_SEARCH','KEYWORD_SEARCH') NOT NULL");
        verify(jdbcTemplate, never()).execute("ALTER TABLE map_verification_histories MODIFY COLUMN status enum('FAILED','SUCCESS','MANUAL_REVIEW_REQUIRED') NOT NULL");
    }

    private Answer<String> enumColumnTypeAnswer(String queryTypeColumnType, String statusColumnType) {
        return invocation -> {
            Object[] arguments = invocation.getArguments();
            String columnName = (String) arguments[3];
            return switch (columnName) {
                case "query_type" -> queryTypeColumnType;
                case "status" -> statusColumnType;
                default -> null;
            };
        };
    }
}
