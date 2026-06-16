package com.mcpruntime.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates that SQL-like arguments don't contain dangerous keywords.
 * <p>
 * Scans the {@code sql} argument for forbidden DDL/DML operations.
 * This is a conservative policy — it errs on the side of blocking
 * when uncertain. For production use, prefer dedicated CRUD tools
 * over a generic "queryDatabase" tool with raw SQL.
 */
public class SqlInjectionPolicy implements ValidationPolicy {

    private static final Logger log = LoggerFactory.getLogger(SqlInjectionPolicy.class);

    private static final Pattern DANGEROUS_PATTERN =
            Pattern.compile("(?i)\\b(DROP|DELETE|TRUNCATE|ALTER|EXEC|EXECUTE|CREATE|INSERT)\\b");

    private final String targetToolName;

    public SqlInjectionPolicy() {
        this("queryDatabase");
    }

    public SqlInjectionPolicy(String targetToolName) {
        this.targetToolName = targetToolName;
    }

    @Override
    public void validate(String toolName, Map<String, Object> args) {
        if (!targetToolName.equals(toolName) || args == null || args.isEmpty()) {
            return;
        }

        Object sqlArg = args.get("sql");
        if (sqlArg == null) {
            return;
        }

        String sql = sqlArg.toString();
        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            log.warn("Blocked SQL injection attempt in tool {}: {}", toolName, sql);
            throw new SecurityException(
                    "SQL contains forbidden operations (DROP, DELETE, TRUNCATE, etc.)");
        }
    }
}
