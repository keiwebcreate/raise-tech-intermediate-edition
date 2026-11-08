-- ユーザー（docs/database-design.md 3.1）
-- 一度実行したこのファイルは書き換えない。変えるときは V3__... のように新しいファイルを足す
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    username       VARCHAR(15)  NOT NULL,
    display_name   VARCHAR(50)  NOT NULL,
    bio            VARCHAR(160),
    icon_image_key VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    -- メールアドレスとユーザー名は小文字にそろえて保存する（Taro と taro を同じ人とみなすため）
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_username CHECK (username ~ '^[a-z0-9_]{3,15}$'),
    CONSTRAINT ck_users_display_name CHECK (length(btrim(display_name)) >= 1)
);
