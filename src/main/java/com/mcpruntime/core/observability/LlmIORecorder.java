package com.mcpruntime.core.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Asynchronous recorder for LLM input/output pairs.
 * <p>
 * Captures every prompt sent to the LLM and every completion received,
 * along with metadata (traceId, sessionId, model info, token count).
 * Recording is fire-and-forget — the calling thread is never blocked.
 * <p>
 * <b>Storage:</b> This recorder ships records to an internal queue.
 * A background thread drains the queue and passes records to registered
 * {@link RecordConsumer}s. In production, wire a consumer that writes
 * to your preferred storage: database, S3, Elasticsearch, etc.
 * <p>
 * <b>Redaction:</b> Use {@link #setRedactor} to install a content
 * redactor that strips PII before records are delivered to consumers.
 */
public class LlmIORecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmIORecorder.class);

    private final BlockingQueue<LlmIORecord> queue = new LinkedBlockingQueue<>(100_000);
    private final ExecutorService executor;
    private volatile LlmContentRedactor redactor = LlmContentRedactor.noop();
    private volatile boolean running = true;

    /**
     * Create a LlmIORecorder with a single background consumer thread.
     */
    public LlmIORecorder() {
        this(1);
    }

    /**
     * Create a LlmIORecorder with a custom number of consumer threads.
     */
    public LlmIORecorder(int consumerThreads) {
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, consumerThreads),
                r -> {
                    Thread t = new Thread(r, "llm-io-recorder");
                    t.setDaemon(true);
                    return t;
                });

        // Start consumer threads
        for (int i = 0; i < Math.max(1, consumerThreads); i++) {
            executor.submit(this::consumeLoop);
        }
    }

    // ========================================================================
    // Configuration
    // ========================================================================

    /**
     * Set the content redactor used to strip sensitive information.
     */
    public void setRedactor(LlmContentRedactor redactor) {
        this.redactor = Objects.requireNonNullElse(redactor, LlmContentRedactor.noop());
    }

    /**
     * Get the current content redactor.
     */
    public LlmContentRedactor getRedactor() {
        return redactor;
    }

    // ========================================================================
    // Recording
    // ========================================================================

    /**
     * Record an LLM interaction (prompt and/or completion).
     * <p>
     * Non-blocking — enqueues the record and returns immediately.
     * If the internal queue is full, the record is dropped and a
     * warning is logged.
     *
     * @return true if the record was enqueued, false if dropped
     */
    public boolean record(LlmIORecord record) {
        if (!running) {
            log.warn("LlmIORecorder is shutdown, dropping record");
            return false;
        }

        // Apply redaction before enqueueing
        LlmIORecord.RecordType type = record.getType();
        String purpose = type == LlmIORecord.RecordType.PROMPT ? "PROMPT" : "COMPLETION";
        LlmContentRedactor.RedactContext ctx =
                new LlmContentRedactor.RedactContext(
                        record.getTraceId(), record.getSessionId(), purpose);
        String redactedContent = redactor.redact(record.getContent(), ctx);

        LlmIORecord redactedRecord = new LlmIORecord(
                record.getTraceId(),
                record.getSessionId(),
                record.getTurnNumber(),
                type,
                record.getModelProvider(),
                record.getModelName(),
                redactedContent,
                record.getTokenCount(),
                record.getMetadata()
        );

        if (!queue.offer(redactedRecord)) {
            log.warn("LLM I/O record queue full, dropping record for trace {}", record.getTraceId());
            return false;
        }
        return true;
    }

    // ========================================================================
    // Shutdown
    // ========================================================================

    /**
     * Shutdown the recorder. Drains remaining records before shutdown.
     */
    public void shutdown() {
        this.running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Current queue depth.
     */
    public int pendingCount() {
        return queue.size();
    }

    // ========================================================================
    // Consumer interface
    // ========================================================================

    /**
     * Consumer for LLM I/O records.
     * <p>
     * Implement this to write records to your preferred storage backend.
     * The consumer is called from a background thread — do not block
     * for extended periods.
     */
    @FunctionalInterface
    public interface RecordConsumer {
        void accept(LlmIORecord record);
    }

    /**
     * Add a consumer that receives all recorded LLM I/O.
     */
    public void addConsumer(RecordConsumer consumer) {
        this.consumers.add(Objects.requireNonNull(consumer));
    }

    private final java.util.List<RecordConsumer> consumers = new CopyOnWriteArrayList<>();

    // ========================================================================
    // Internal
    // ========================================================================

    private void consumeLoop() {
        while (running || !queue.isEmpty()) {
            try {
                LlmIORecord record = queue.poll(1, TimeUnit.SECONDS);
                if (record != null) {
                    for (RecordConsumer consumer : consumers) {
                        try {
                            consumer.accept(record);
                        } catch (Exception e) {
                            log.error("LLM I/O consumer threw exception", e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ========================================================================
    // Inner Types — LlmIORecord
    // ========================================================================

    /**
     * A single LLM I/O record — the atomic unit of LLM interaction logging.
     * <p>
     * Immutable after creation. Each record captures either the PROMPT
     * (what was sent to the model) or the COMPLETION (what the model returned).
     */
    public static class LlmIORecord {

        public enum RecordType {
            PROMPT,
            COMPLETION
        }

        private final String traceId;
        private final String sessionId;
        private final int turnNumber;
        private final RecordType type;
        private final String modelProvider;
        private final String modelName;
        private final String content;
        private final int tokenCount;
        private final Map<String, Object> metadata;
        private final Instant timestamp;

        public LlmIORecord(String traceId, String sessionId, int turnNumber,
                           RecordType type, String modelProvider, String modelName,
                           String content, int tokenCount,
                           Map<String, Object> metadata) {
            this.traceId = traceId;
            this.sessionId = sessionId;
            this.turnNumber = turnNumber;
            this.type = type;
            this.modelProvider = modelProvider;
            this.modelName = modelName;
            this.content = content;
            this.tokenCount = tokenCount;
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            this.timestamp = Instant.now();
        }

        // --- Builder convenience ---

        public static Builder prompt() {
            return new Builder().type(RecordType.PROMPT);
        }

        public static Builder completion() {
            return new Builder().type(RecordType.COMPLETION);
        }

        // --- Accessors ---

        public String getTraceId() { return traceId; }
        public String getSessionId() { return sessionId; }
        public int getTurnNumber() { return turnNumber; }
        public RecordType getType() { return type; }
        public String getModelProvider() { return modelProvider; }
        public String getModelName() { return modelName; }
        public String getContent() { return content; }
        public int getTokenCount() { return tokenCount; }
        public Map<String, Object> getMetadata() { return metadata; }
        public Instant getTimestamp() { return timestamp; }

        // --- Builder ---

        public static class Builder {
            private String traceId;
            private String sessionId;
            private int turnNumber;
            private RecordType type;
            private String modelProvider;
            private String modelName;
            private String content;
            private int tokenCount;
            private Map<String, Object> metadata;

            public Builder traceId(String traceId) { this.traceId = traceId; return this; }
            public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
            public Builder turnNumber(int turnNumber) { this.turnNumber = turnNumber; return this; }
            public Builder type(RecordType type) { this.type = type; return this; }
            public Builder modelProvider(String modelProvider) { this.modelProvider = modelProvider; return this; }
            public Builder modelName(String modelName) { this.modelName = modelName; return this; }
            public Builder content(String content) { this.content = content; return this; }
            public Builder tokenCount(int tokenCount) { this.tokenCount = tokenCount; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

            public LlmIORecord build() {
                return new LlmIORecord(traceId, sessionId, turnNumber,
                        type, modelProvider, modelName, content, tokenCount, metadata);
            }
        }
    }
}
