-- コメント・返信へのいいね（docs/database-design.md 3.3.1・docs/features/like.md 4.4）
-- 投稿へのいいね（likes）と同じ形。一度実行したこのファイルは書き換えない
CREATE TABLE comment_likes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- いいねしたコメント。コメントを消したら、いいねも一緒に消える
    comment_id BIGINT      NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    -- いいねした人。ユーザーを消したら、その人のいいねも一緒に消える
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 同じ人が同じコメントに2回いいねできない。
    -- この一意制約の索引は、コメントごとのいいね数と「自分がいいねしたか」を数えるときにも使う
    CONSTRAINT uq_comment_likes UNIQUE (comment_id, user_id)
);

-- いいねした人の一覧：コメントごとに、新しくいいねした順（ID の大きい順）
CREATE INDEX ix_comment_likes_comment_id_id ON comment_likes (comment_id, id DESC);
