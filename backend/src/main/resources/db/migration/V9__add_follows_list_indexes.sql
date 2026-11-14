-- フォロー中・フォロワーの一覧（docs/features/profile.md 4.5）を、新しくフォローした順（id の大きい順）に速く読むための索引。
-- 「その人の行だけを id の順に」読めるように、(人, id) の2列の索引にする

-- フォロー中の一覧：follower_id = その人 の行を id の大きい順に
CREATE INDEX ix_follows_follower_id_id ON follows (follower_id, id);

-- フォロワーの一覧：following_id = その人 の行を id の大きい順に。
-- 今の ix_follows_following_id（following_id だけ）は、この索引の先頭の列と同じで要らなくなるので置きかえる
-- （フォロワー数を数えるのにも、この索引が使える）
DROP INDEX ix_follows_following_id;
CREATE INDEX ix_follows_following_id_id ON follows (following_id, id);
