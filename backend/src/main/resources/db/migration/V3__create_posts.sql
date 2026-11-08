-- 投稿（docs/database-design.md 3.2）。画像・動画（post_media）は、その機能のときに V5 以降で足す
-- 一度実行したこのファイルは書き換えない
CREATE TABLE posts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 投稿した人。ユーザーを消したら、その人の投稿も一緒に消える
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 本文。varchar(280) は「文字（コードポイント）」で数える。絵文字も1文字
    content    VARCHAR(280) NOT NULL,
    -- 編集した日時。NULL なら編集していない（「編集済み」の表示に使う）
    edited_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 空白・改行だけの本文は不可（前後の空白はアプリが取り除いてから保存する）
    CONSTRAINT ck_posts_content CHECK (length(btrim(content, E' \t\r\n')) >= 1)
);

-- 「すべて」タブ：新しい順（同じ時刻なら ID の大きい順）に20件ずつ読む
CREATE INDEX ix_posts_created_at ON posts (created_at DESC, id DESC);
-- 「フォロー中」タブ・プロフィールの投稿：人ごとに新しい順
CREATE INDEX ix_posts_user_id_created_at ON posts (user_id, created_at DESC, id DESC);
