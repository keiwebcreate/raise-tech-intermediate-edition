package io.github.keiwebcreate.raisetechtimeline.post;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * posts テーブルの読み書き（MyBatis）。SQL は src/main/resources/mapper/PostMapper.xml に書く。
 */
@Mapper
public interface PostMapper {

	/**
	 * タイムライン。新しい順（同じ時刻なら ID の大きい順）。
	 *
	 * @param viewerId 見ている人（ログインしている人）
	 * @param following true なら「フォロー中」タブ（自分とフォローしている人の投稿だけ）
	 * @param beforeId この投稿より古いものだけ（続きを読むとき）。null なら最新から
	 * @param afterId この投稿より新しいものだけ（「新しい投稿 N件」を押したとき）。null なら絞らない
	 * @param limit 取る件数
	 */
	List<Post> findTimeline(@Param("viewerId") long viewerId, @Param("following") boolean following,
			@Param("beforeId") @Nullable Long beforeId, @Param("afterId") @Nullable Long afterId,
			@Param("limit") int limit);

	/**
	 * afterId の投稿より新しい、自分以外の投稿の数（「新しい投稿 N件」）。max 件で数えるのをやめる。
	 *
	 * @param afterId 画面で一番上の投稿。null なら（一覧が空なら）すべてを数える
	 */
	int countNewer(@Param("viewerId") long viewerId, @Param("following") boolean following,
			@Param("afterId") @Nullable Long afterId, @Param("max") int max);

	Optional<Post> findById(@Param("id") long id);

	/** 1行足して、できた投稿の ID を返す */
	long insert(@Param("userId") long userId, @Param("content") String content);

	/** 本人の投稿だけ本文を変える。変えた行の数（他人の投稿・ない投稿なら 0）を返す */
	int updateContent(@Param("id") long id, @Param("userId") long userId, @Param("content") String content);

	/** 本人の投稿だけ消す。消した行の数（他人の投稿・ない投稿なら 0）を返す */
	int delete(@Param("id") long id, @Param("userId") long userId);

}
