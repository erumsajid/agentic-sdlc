CREATE SCHEMA URL_SHORTENER;
CREATE TABLE short_url (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    target_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    click_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_short_url_code CHECK (length(code) BETWEEN 3 AND 32),
    CONSTRAINT chk_short_url_click_count CHECK (click_count >= 0)
);

CREATE INDEX idx_short_url_expires_at ON short_url (expires_at);

CREATE TABLE short_url_click (
    id BIGSERIAL PRIMARY KEY,
    short_url_id UUID NOT NULL REFERENCES short_url(id) ON DELETE CASCADE,
    accessed_at TIMESTAMPTZ NOT NULL,
    referrer_host VARCHAR(255)
);

CREATE INDEX idx_short_url_click_url_time ON short_url_click (short_url_id, accessed_at);
