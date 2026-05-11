package com.toggle.global.config;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OwnerApplicationSchemaMigrator implements CommandLineRunner {

    private static final String TABLE_NAME = "owner_applications";
    private static final String MAP_VERIFICATION_TABLE_NAME = "map_verification_histories";
    private static final String MAP_VERIFICATION_QUERY_TYPE_ENUM =
        "enum('NAME_AND_ADDRESS','ADDRESS_ONLY','ADDRESS_SEARCH','KEYWORD_SEARCH')";
    private static final String MAP_VERIFICATION_STATUS_ENUM =
        "enum('FAILED','SUCCESS','MANUAL_REVIEW_REQUIRED')";

    private final JdbcTemplate jdbcTemplate;

    public OwnerApplicationSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureColumn("business_license_original_name", "VARCHAR(255) NULL");
        ensureColumn("business_license_original_filename", "VARCHAR(255) NULL");
        ensureColumn("business_license_stored_path", "VARCHAR(255) NULL");
        ensureColumn("business_license_object_key", "VARCHAR(1000) NULL");
        ensureColumn("business_license_content_type", "VARCHAR(100) NULL");
        ensureColumn("business_license_size", "BIGINT NULL");
        ensureColumn(
            "business_license_uploaded_at",
            "DATETIME(6) NULL"
        );
        ensureColumn(
            "business_license_expires_at",
            "DATETIME(6) NULL"
        );
        ensureEnumColumn(
            MAP_VERIFICATION_TABLE_NAME,
            "query_type",
            MAP_VERIFICATION_QUERY_TYPE_ENUM,
            "MODIFY COLUMN query_type " + MAP_VERIFICATION_QUERY_TYPE_ENUM + " NOT NULL"
        );
        ensureEnumColumn(
            MAP_VERIFICATION_TABLE_NAME,
            "status",
            MAP_VERIFICATION_STATUS_ENUM,
            "MODIFY COLUMN status " + MAP_VERIFICATION_STATUS_ENUM + " NOT NULL"
        );
    }

    private void ensureColumn(String columnName, String ddlType) {
        ensureColumn(TABLE_NAME, columnName, ddlType);
    }

    private void ensureColumn(String tableName, String columnName, String ddlType) {
        Integer columnCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            tableName,
            columnName
        );

        if (columnCount != null && columnCount > 0) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddlType);
    }

    private void ensureEnumColumn(String tableName, String columnName, String expectedColumnType, String alterClause) {
        String columnType = jdbcTemplate.queryForObject(
            """
            SELECT COLUMN_TYPE
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
            String.class,
            tableName,
            columnName
        );

        if (expectedColumnType.equalsIgnoreCase(columnType)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE " + tableName + " " + alterClause);
    }
}
