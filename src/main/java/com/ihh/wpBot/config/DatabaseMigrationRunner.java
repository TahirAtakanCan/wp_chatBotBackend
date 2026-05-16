package com.ihh.wpBot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Locale;

@Component
@Order(1)
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            String dbKind = productName == null ? "" : productName.toLowerCase(Locale.ROOT);

            migrateEnumLikeColumn(connection, dbKind, "messages", "message_type");
            migrateEnumLikeColumn(connection, dbKind, "conversations", "last_message_type");
        } catch (Exception e) {
            log.warn("Database enum->varchar migration kontrolü sırasında hata: {}", e.getMessage());
        }
    }

    private void migrateEnumLikeColumn(Connection connection, String dbKind, String tableName, String columnName) {
        try {
            String currentType = findColumnType(connection, tableName, columnName);
            if (currentType == null) {
                log.debug("Kolon bulunamadı, migration atlandı: {}.{}", tableName, columnName);
                return;
            }

            String normalized = currentType.toUpperCase(Locale.ROOT);
            if (normalized.contains("VARCHAR") || normalized.contains("CHARACTER VARYING")) {
                log.debug("Kolon zaten VARCHAR: {}.{} ({})", tableName, columnName, currentType);
                return;
            }

            String alterSql = buildAlterSql(dbKind, tableName, columnName);
            jdbcTemplate.execute(alterSql);
            log.info("{}.{} column migrated to VARCHAR(32)", tableName, columnName);
        } catch (Exception e) {
            log.debug("{}.{} migration skipped: {}", tableName, columnName, e.getMessage());
        }
    }

    private String findColumnType(Connection connection, String tableName, String columnName) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String type = readType(metaData, tableName, columnName);
            if (type != null) {
                return type;
            }
            return readType(metaData, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private String readType(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                return rs.getString("TYPE_NAME");
            }
            return null;
        }
    }

    private String buildAlterSql(String dbKind, String tableName, String columnName) {
        if (dbKind.contains("mysql")) {
            return "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " VARCHAR(32)";
        }
        if (dbKind.contains("postgresql")) {
            return "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " TYPE VARCHAR(32)";
        }
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " SET DATA TYPE VARCHAR(32)";
    }
}
