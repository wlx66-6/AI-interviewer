-- LLM 调用用量与成本统计。
-- 每次 Chat / Embedding 调用落一行；写入在独立事务（REQUIRES_NEW）中完成，失败不影响主链路。
CREATE TABLE llm_token_usage (
    id                BIGSERIAL PRIMARY KEY,
    provider_id       VARCHAR(64) NOT NULL,
    model             VARCHAR(128),
    kind              VARCHAR(16) NOT NULL,
    source            VARCHAR(32) NOT NULL,
    prompt_tokens     INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens      INTEGER NOT NULL DEFAULT 0,
    latency_ms        BIGINT,
    success           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_token_usage_created_at ON llm_token_usage (created_at);
CREATE INDEX idx_llm_token_usage_provider_created ON llm_token_usage (provider_id, created_at);