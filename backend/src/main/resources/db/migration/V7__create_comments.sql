-- コメントと返信（docs/database-design.md 3.3・docs/features/comment.md）
-- 一度実行したこのファイルは書き換えない
CREATE TABLE comments (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- コメントした投稿。投稿を消したら、コメントも一緒に消える
    post_id           BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- コメントした人。ユーザーを消したら、その人のコメントも一緒に消える
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 返信先のコメント（返信の返信なら、返信の行）。NULL なら投稿への直接のコメント。
    -- 返信先を消したら、この返信も一緒に消える（その下の返信も、次々に消える）
    parent_comment_id BIGINT       REFERENCES comments (id) ON DELETE CASCADE,
    -- 返信が並ぶ「一番上のコメント」。画面で返信を1段にまとめて並べるために持つ。NULL なら自分が一番上
    root_comment_id   BIGINT       REFERENCES comments (id) ON DELETE CASCADE,
    -- 本文。varchar(280) は「文字（コードポイント）」で数える。絵文字も1文字
    content           VARCHAR(280) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 空白・改行だけの本文は不可（前後の空白はアプリが取り除いてから保存する）
    CONSTRAINT ck_comments_content CHECK (length(btrim(content, E' \t\r\n')) >= 1),
    -- 返信先と一番上のコメントは、両方あるか、両方ないか
    CONSTRAINT ck_comments_reply CHECK ((parent_comment_id IS NULL) = (root_comment_id IS NULL))
);

-- 投稿への直接のコメント：古い順に50件ずつ読む・投稿ごとのコメント数を数える
-- （WHERE で返信を除いた「部分索引」。返信の行は入らないので小さい）
CREATE INDEX ix_comments_post_id_created_at ON comments (post_id, created_at, id)
    WHERE parent_comment_id IS NULL;
-- 一番上のコメントの下の返信：古い順に読む・返信の数を数える
CREATE INDEX ix_comments_root_comment_id_created_at ON comments (root_comment_id, created_at, id);
-- 直接の返信の数を数える。返信先を消したときに、下の返信を探すのにも使う
CREATE INDEX ix_comments_parent_comment_id ON comments (parent_comment_id);
