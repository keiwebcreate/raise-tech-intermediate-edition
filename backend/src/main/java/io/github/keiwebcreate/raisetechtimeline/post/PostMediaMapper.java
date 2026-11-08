package io.github.keiwebcreate.raisetechtimeline.post;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * post_media テーブルの読み書き（MyBatis）。SQL は src/main/resources/mapper/PostMediaMapper.xml に書く。
 */
@Mapper
public interface PostMediaMapper {

	/**
	 * いくつかの投稿の画像・動画を、まとめて1回の SQL で読む（タイムラインの20件を1件ずつ読まないように）。
	 * 投稿ごと・並び順に並べて返す。postIds は空にしない
	 */
	List<PostMedia> findByPostIds(@Param("postIds") List<Long> postIds);

	void insert(@Param("postId") long postId, @Param("mediaType") String mediaType, @Param("s3Key") String s3Key,
			@Param("position") int position);

	/** その投稿の画像・動画の行をすべて消す（編集で並べ直すとき） */
	void deleteByPostId(@Param("postId") long postId);

}
