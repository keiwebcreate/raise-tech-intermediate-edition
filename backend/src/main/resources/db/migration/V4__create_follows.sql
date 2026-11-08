-- フォロー（docs/database-design.md 3.5）。「フォロー中」タブで使うので、投稿と一緒に作る。
-- フォローの画面・API は、プロフィール・フォローの機能のときに作る
CREATE TABLE follows (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- フォロー「する」人
    follower_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- フォロー「される」人
    following_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 同じ人を2回フォローできない（この一意制約の索引は「フォロー中」タブの絞り込みにも使う）
    CONSTRAINT uq_follows UNIQUE (follower_id, following_id),
    -- 自分自身はフォローできない
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> following_id)
);

-- フォロワー数・フォロワーの一覧
CREATE INDEX ix_follows_following_id ON follows (following_id);
