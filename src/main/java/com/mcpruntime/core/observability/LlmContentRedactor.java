package com.mcpruntime.core.observability;

/**
 * Content redaction interface for LLM I/O recording.
 * <p>
 * Use this to strip sensitive information (PII, credentials, internal URLs)
 * from LLM prompts and completions before they are persisted.
 * <p>
 * The default implementation ({@link NoopRedactor}) passes content through
 * unchanged. In production, provide a custom implementation that applies
 * regex-based or AI-based redaction rules.
 */
@FunctionalInterface
public interface LlmContentRedactor {

    /**
     * Redact sensitive content from an LLM prompt or completion.
     *
     * @param content raw content string
     * @param context provides metadata (sessionId, redaction purpose, etc.)
     * @return redacted content
     */
    String redact(String content, RedactContext context);

    /**
     * Context information for the redaction decision.
     */
    final class RedactContext {

        private final String traceId;
        private final String sessionId;
        private final String purpose; // "PROMPT" or "COMPLETION"

        public RedactContext(String traceId, String sessionId, String purpose) {
            this.traceId = traceId;
            this.sessionId = sessionId;
            this.purpose = purpose;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getPurpose() {
            return purpose;
        }
    }

    /**
     * No-op redactor that passes content through unchanged.
     * Default implementation used in development environments.
     */
    static LlmContentRedactor noop() {
        return (content, ctx) -> content;
    }
}
