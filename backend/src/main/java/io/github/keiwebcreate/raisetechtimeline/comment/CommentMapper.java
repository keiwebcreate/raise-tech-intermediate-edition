package io.github.keiwebcreate.raisetechtimeline.comment;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * comments テーブルの読み書き（MyBatis）。SQL は src/main/resources/mapper/CommentMapper.xml に書く。
 * 読むときは、見ている人（viewerId）がいいねしているかも一緒に読む。
 */
@Mapper
public interface CommentMapper {

	boolean postExists(@Param("postId") long postId);

	boolean commentExists(@Param("id") long id);

	/**
	 * 投稿への直接のコメント。古い順。
	 *
	 * @param afterId このコメントより後のものだけ（続きを読むとき）。null なら最初から
	 */
	List<Comment> findTopLevel(@Param("postId") long postId, @Param("viewerId") long viewerId,
			@Param("afterId") @Nullable Long afterId, @Param("limit") int limit);

	/** 一番上のコメントの下に並ぶ返信すべて（返信の返信も含む）。古い順 */
	List<Comment> findReplies(@Param("rootId") long rootId, @Param("viewerId") long viewerId);

	Optional<Comment> findById(@Param("id") long id, @Param("viewerId") long viewerId);

	/** 投稿に直接コメントする。足した行の ID を返す */
	long insertComment(@Param("postId") long postId, @Param("userId") long userId, @Param("content") String content);

	/**
	 * 返信する。返信先が「この投稿のコメント」のときだけ足す。
	 *
	 * @return 足した行の ID。返信先がない・別の投稿のコメントなら null
	 */
	@Nullable
	Long insertReply(@Param("postId") long postId, @Param("userId") long userId, @Param("parentId") long parentId,
			@Param("content") String content);

	/** 本人のコメントを消す（下の返信・いいねも一緒に消える）。消した行の数を返す。他人のものなら 0 */
	int delete(@Param("id") long id, @Param("userId") long userId);

}
