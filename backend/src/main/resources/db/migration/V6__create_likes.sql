-- 投稿へのいいね（docs/database-design.md 3.4・docs/features/like.md）
-- 一度実行したこのファイルは書き換えない
CREATE TABLE likes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- いいねした投稿。投稿を消したら、いいねも一緒に消える
    post_id    BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- いいねした人。ユーザーを消したら、その人のいいねも一緒に消える
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 同じ人が同じ投稿に2回いいねできない。
    -- この一意制約の索引は、投稿ごとのいいね数と「自分がいいねしたか」を数えるときにも使う
    CONSTRAINT uq_likes UNIQUE (post_id, user_id)
);

-- いいねした人の一覧：投稿ごとに、新しくいいねした順（ID の大きい順）
CREATE INDEX ix_likes_post_id_id ON likes (post_id, id DESC);
