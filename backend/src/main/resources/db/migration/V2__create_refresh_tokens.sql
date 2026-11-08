-- リフレッシュトークン（docs/database-design.md 3.6、docs/features/authentication.md 7）
-- トークンそのものは保存せず、SHA-256 のハッシュ（16進数64文字）だけを持つ。DB が漏れても、そのままでは使えない
CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- 取り替え・ログアウトで使えなくした日時。NULL なら有効
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- その人のトークンをまとめて取り消すとき（使い回しを見つけたとき）
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
-- 期限切れをまとめて消すとき（1日1回）
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
