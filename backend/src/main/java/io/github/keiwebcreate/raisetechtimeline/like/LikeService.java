package io.github.keiwebcreate.raisetechtimeline.like;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.common.error.CommentNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikeResponse;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikersResponse;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;

/**
 * 投稿とコメント・返信へのいいね（docs/features/like.md）。自分のものにも、他の人のものにもいいねできる。
 */
@Service
public class LikeService {

	/** いいねした人の一覧を1回に読む件数 */
	public static final int LIKERS_PAGE_SIZE = 20;

	private final LikeMapper likeMapper;
	private final MediaService mediaService;

	public LikeService(LikeMapper likeMapper, MediaService mediaService) {
		this.likeMapper = likeMapper;
		this.mediaService = mediaService;
	}

	/**
	 * いいねする。もういいねしていても、エラーにせず「いいね済み」を返す。
	 *
	 * @throws PostNotFoundException 投稿がない
	 */
	@Transactional
	public LikeResponse like(long userId, long postId) {
		requirePost(postId);
		try {
			likeMapper.insert(postId, userId);
		} catch (DataIntegrityViolationException e) {
			// 確かめた直後に投稿が消された（外部キーの違反）。500 ではなく「見つかりません」にする
			throw new PostNotFoundException();
		}
		return new LikeResponse(true, likeMapper.count(postId));
	}

	/**
	 * いいねを取り消す。いいねしていなくても、エラーにしない。
	 *
	 * @throws PostNotFoundException 投稿がない
	 */
	@Transactional
	public LikeResponse unlike(long userId, long postId) {
		requirePost(postId);
		likeMapper.delete(postId, userId);
		return new LikeResponse(false, likeMapper.count(postId));
	}

	/**
	 * いいねした人。新しくいいねした順に20件。
	 *
	 * @param before 続きを読むとき、前の返事の nextBefore
	 * @throws PostNotFoundException 投稿がない
	 */
	@Transactional(readOnly = true)
	public LikersResponse likers(long postId, @Nullable Long before) {
		requirePost(postId);
		// 1件多く取って、続きがあるかを知る
		return toLikersResponse(likeMapper.findLikers(postId, before, LIKERS_PAGE_SIZE + 1));
	}

	// ===== コメント・返信へのいいね（like.md 4.4。投稿へのいいねと同じ決まり） =====

	/**
	 * コメント・返信にいいねする。もういいねしていても、エラーにせず「いいね済み」を返す。
	 *
	 * @throws CommentNotFoundException コメントがない
	 */
	@Transactional
	public LikeResponse likeComment(long userId, long commentId) {
		requireComment(commentId);
		try {
			likeMapper.insertCommentLike(commentId, userId);
		} catch (DataIntegrityViolationException e) {
			// 確かめた直後にコメントが消された（外部キーの違反）
			throw new CommentNotFoundException();
		}
		return new LikeResponse(true, likeMapper.countCommentLikes(commentId));
	}

	/**
	 * コメント・返信のいいねを取り消す。いいねしていなくても、エラーにしない。
	 *
	 * @throws CommentNotFoundException コメントがない
	 */
	@Transactional
	public LikeResponse unlikeComment(long userId, long commentId) {
		requireComment(commentId);
		likeMapper.deleteCommentLike(commentId, userId);
		return new LikeResponse(false, likeMapper.countCommentLikes(commentId));
	}

	/**
	 * コメント・返信にいいねした人。新しくいいねした順に20件。
	 *
	 * @throws CommentNotFoundException コメントがない
	 */
	@Transactional(readOnly = true)
	public LikersResponse commentLikers(long commentId, @Nullable Long before) {
		requireComment(commentId);
		return toLikersResponse(likeMapper.findCommentLikers(commentId, before, LIKERS_PAGE_SIZE + 1));
	}

	/** 1件多く読んだ rows から、20件と「続きがあるか」を返事にする */
	private LikersResponse toLikersResponse(List<Liker> rows) {
		List<Liker> page = rows.stream().limit(LIKERS_PAGE_SIZE).toList();
		Long nextBefore = rows.size() > LIKERS_PAGE_SIZE ? page.getLast().likeId() : null;
		List<LikersResponse.User> users = page.stream()
				.map(liker -> new LikersResponse.User(liker.userId(), liker.username(), liker.displayName(),
						mediaService.iconUrl(liker.iconImageKey())))
				.toList();
		return new LikersResponse(users, nextBefore);
	}

	private void requirePost(long postId) {
		if (!likeMapper.postExists(postId)) {
			throw new PostNotFoundException();
		}
	}

	private void requireComment(long commentId) {
		if (!likeMapper.commentExists(commentId)) {
			throw new CommentNotFoundException();
		}
	}

}
