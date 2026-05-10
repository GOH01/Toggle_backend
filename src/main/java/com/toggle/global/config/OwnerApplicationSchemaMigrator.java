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
    }

    private void ensureColumn(String columnName, String ddlType) {
        Integer columnCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            TABLE_NAME,
            columnName
        );

        if (columnCount != null && columnCount > 0) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + ddlType);
    }
}
