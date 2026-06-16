package com.mcpruntime.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SqlInjectionPolicy}.
 */
class SqlInjectionPolicyTest {

    private SqlInjectionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SqlInjectionPolicy("queryDatabase");
    }

    @Test
    void shouldAllowSafeSql() {
        assertDoesNotThrow(() ->
                policy.validate("queryDatabase", Map.of("sql", "SELECT * FROM users WHERE id = 1")));
    }

    @Test
    void shouldAllowSelectWithOrderBy() {
        assertDoesNotThrow(() ->
                policy.validate("queryDatabase", Map.of("sql", "SELECT name, email FROM users ORDER BY name")));
    }

    @Test
    void shouldBlockDropStatement() {
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "DROP TABLE users")));
    }

    @Test
    void shouldBlockDeleteStatement() {
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "DELETE FROM users")));
    }

    @Test
    void shouldBlockTruncateStatement() {
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "TRUNCATE TABLE users")));
    }

    @Test
    void shouldBlockAlterStatement() {
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "ALTER TABLE users DROP COLUMN email")));
    }

    @Test
    void shouldBlockExecStatement() {
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "EXEC sp_delete_user")));
    }

    @Test
    void shouldBlockCreateStatement() {
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "CREATE TABLE hack (id INT)")));
    }

    @Test
    void shouldBlockCaseInsensitiveSql() {
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "drop table users")));
        assertThrows(SecurityException.class, () ->
                policy.validate("queryDatabase", Map.of("sql", "Delete FROM users")));
    }

    @Test
    void shouldNotBlockWhenToolNameDoesNotMatch() {
        // Policy configured for "queryDatabase", but this is a different tool
        assertDoesNotThrow(() ->
                policy.validate("search", Map.of("sql", "DROP TABLE users")));
    }

    @Test
    void shouldHandleMissingSqlArg() {
        assertDoesNotThrow(() ->
                policy.validate("queryDatabase", Map.of("query", "DROP TABLE users")));
    }

    @Test
    void shouldHandleNullArgs() {
        assertDoesNotThrow(() ->
                policy.validate("queryDatabase", null));
    }

    @Test
    void shouldUseDefaultToolName() {
        SqlInjectionPolicy defaultPolicy = new SqlInjectionPolicy();
        assertThrows(SecurityException.class, () ->
                defaultPolicy.validate("queryDatabase", Map.of("sql", "DROP TABLE users")));
    }
}
