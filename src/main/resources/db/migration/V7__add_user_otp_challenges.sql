CREATE TABLE user_otp_challenges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resend_available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    send_count INTEGER NOT NULL DEFAULT 1,
    send_window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    verification_token_hash VARCHAR(64),
    verification_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_user_otp_purpose UNIQUE (user_id, purpose),
    CONSTRAINT chk_user_otp_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_user_otp_send_count CHECK (send_count > 0)
);

CREATE INDEX idx_user_otp_expires_at ON user_otp_challenges(expires_at);
