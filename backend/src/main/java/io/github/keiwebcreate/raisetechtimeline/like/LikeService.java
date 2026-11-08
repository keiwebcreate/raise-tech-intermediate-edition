package io.github.keiwebcreate.raisetechtimeline.like;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikeResponse;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikersResponse;

/**
 * 投稿へのいいね（docs/features/like.md）。自分の投稿にも、他の人の投稿にもいいねできる。
 */
@Service
public class LikeService {

	/** いいねした人の一覧を1回に読む件数 */
	public static final int LIKERS_PAGE_SIZE = 20;

	private final LikeMapper likeMapper;

	public LikeService(LikeMapper likeMapper) {
		this.likeMapper = likeMapper;
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
		List<Liker> rows = likeMapper.findLikers(postId, before, LIKERS_PAGE_SIZE + 1);
		List<Liker> page = rows.stream().limit(LIKERS_PAGE_SIZE).toList();
		Long nextBefore = rows.size() > LIKERS_PAGE_SIZE ? page.getLast().likeId() : null;
		List<LikersResponse.User> users = page.stream()
				.map(liker -> new LikersResponse.User(liker.userId(), liker.username(), liker.displayName()))
				.toList();
		return new LikersResponse(users, nextBefore);
	}

	private void requirePost(long postId) {
		if (!likeMapper.postExists(postId)) {
			throw new PostNotFoundException();
		}
	}

}
