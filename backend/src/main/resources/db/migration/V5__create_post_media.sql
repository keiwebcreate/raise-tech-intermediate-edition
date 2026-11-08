-- 投稿の画像・動画（docs/database-design.md 3.2.1）。ファイル本体は S3 に置き、ここには S3 のキーだけ持つ
-- 一度実行したこのファイルは書き換えない
CREATE TABLE post_media (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 付いている投稿。投稿を消したら、この行も一緒に消える（S3 のファイルはアプリが消す）
    post_id    BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- 画像（image）か動画（video）か
    media_type VARCHAR(10)  NOT NULL,
    -- S3 のキー（例：posts/2026/11/ランダムな文字列.webp）。サーバーが決める
    s3_key     VARCHAR(255) NOT NULL,
    -- 並び順（1〜4）
    position   SMALLINT     NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_post_media_type CHECK (media_type IN ('image', 'video')),
    CONSTRAINT ck_post_media_position CHECK (position BETWEEN 1 AND 4),
    CONSTRAINT uq_post_media_s3_key UNIQUE (s3_key),
    -- 同じ投稿に同じ並び順は2つない。position が1〜4なので、1つの投稿に4つまで。
    -- この一意制約の索引は「投稿ごとに画像・動画を読む」ときにも使う
    -- 「動画は1本だけ」「画像と動画を混ぜない」は1行では確かめられないので、サーバーで確かめる
    CONSTRAINT uq_post_media_position UNIQUE (post_id, position)
);
