package io.github.keiwebcreate.raisetechtimeline.like;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * likes テーブルの読み書き（MyBatis）。SQL は src/main/resources/mapper/LikeMapper.xml に書く。
 */
@Mapper
public interface LikeMapper {

	boolean postExists(@Param("postId") long postId);

	/** いいねを足す。もういいねしていたら何もしない（足した行の数を返す。0 か 1） */
	int insert(@Param("postId") long postId, @Param("userId") long userId);

	/** いいねを消す。いいねしていなければ何もしない */
	int delete(@Param("postId") long postId, @Param("userId") long userId);

	long count(@Param("postId") long postId);

	/**
	 * いいねした人。新しくいいねした順。
	 *
	 * @param beforeId このいいね（likes.id）より前のものだけ（続きを読むとき）。null なら最新から
	 */
	List<Liker> findLikers(@Param("postId") long postId, @Param("beforeId") @Nullable Long beforeId,
			@Param("limit") int limit);

	// ===== コメント・返信へのいいね（comment_likes。上の投稿へのいいねと同じ形） =====

	boolean commentExists(@Param("commentId") long commentId);

	int insertCommentLike(@Param("commentId") long commentId, @Param("userId") long userId);

	int deleteCommentLike(@Param("commentId") long commentId, @Param("userId") long userId);

	long countCommentLikes(@Param("commentId") long commentId);

	/** @param beforeId このいいね（comment_likes.id）より前のものだけ。null なら最新から */
	List<Liker> findCommentLikers(@Param("commentId") long commentId, @Param("beforeId") @Nullable Long beforeId,
			@Param("limit") int limit);

}
