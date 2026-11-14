package io.github.keiwebcreate.raisetechtimeline.follow;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.common.error.CannotFollowSelfException;
import io.github.keiwebcreate.raisetechtimeline.common.error.UserNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.follow.dto.FollowResponse;
import io.github.keiwebcreate.raisetechtimeline.follow.dto.FollowUsersResponse;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;

/**
 * フォロー・解除と、フォロー中・フォロワーの一覧（docs/features/profile.md 4章）。
 * 「誰が」フォローするかはログインしている本人（URL に指定させないので、他人のフォローは変えられない）。
 */
@Service
public class FollowService {

	/** 一覧を1回に読む件数 */
	public static final int PAGE_SIZE = 20;

	private final FollowMapper followMapper;
	private final MediaService mediaService;

	public FollowService(FollowMapper followMapper, MediaService mediaService) {
		this.followMapper = followMapper;
		this.mediaService = mediaService;
	}

	/**
	 * フォローする。もうフォローしていても、エラーにせず「フォロー済み」を返す（4.1）。
	 *
	 * @throws UserNotFoundException 相手がいない
	 * @throws CannotFollowSelfException 自分自身
	 */
	@Transactional
	public FollowResponse follow(long userId, String username) {
		long targetId = findUserId(username);
		if (targetId == userId) {
			throw new CannotFollowSelfException();
		}
		try {
			followMapper.insert(userId, targetId);
		} catch (DataIntegrityViolationException e) {
			// 確かめた直後に相手が退会した（外部キーの違反）。500 ではなく「見つかりません」にする
			throw new UserNotFoundException();
		}
		return new FollowResponse(true, followMapper.countFollowers(targetId));
	}

	/**
	 * フォローを解除する。フォローしていなくても、エラーにしない。
	 *
	 * @throws UserNotFoundException 相手がいない
	 */
	@Transactional
	public FollowResponse unfollow(long userId, String username) {
		long targetId = findUserId(username);
		followMapper.delete(userId, targetId);
		return new FollowResponse(false, followMapper.countFollowers(targetId));
	}

	/**
	 * その人がフォローしている人。新しくフォローした順に20件。
	 *
	 * @param before 続きを読むとき、前の返事の nextBefore
	 * @throws UserNotFoundException その人がいない
	 */
	@Transactional(readOnly = true)
	public FollowUsersResponse following(long viewerId, String username, @Nullable Long before) {
		// 1件多く取って、続きがあるかを知る
		return toResponse(followMapper.findFollowing(findUserId(username), viewerId, before, PAGE_SIZE + 1));
	}

	/**
	 * その人をフォローしている人。新しくフォローされた順に20件。
	 *
	 * @throws UserNotFoundException その人がいない
	 */
	@Transactional(readOnly = true)
	public FollowUsersResponse followers(long viewerId, String username, @Nullable Long before) {
		return toResponse(followMapper.findFollowers(findUserId(username), viewerId, before, PAGE_SIZE + 1));
	}

	private long findUserId(String username) {
		// ユーザー名は小文字で保存している（Taro と taro を同じ人とみなす）
		return followMapper.findUserId(username.toLowerCase(Locale.ROOT)).orElseThrow(UserNotFoundException::new);
	}

	/** 1件多く読んだ rows から、20件と「続きがあるか」を返事にする */
	private FollowUsersResponse toResponse(List<FollowUser> rows) {
		List<FollowUser> page = rows.stream().limit(PAGE_SIZE).toList();
		Long nextBefore = rows.size() > PAGE_SIZE ? page.getLast().followId() : null;
		List<FollowUsersResponse.User> users = page.stream()
				.map(row -> new FollowUsersResponse.User(row.userId(), row.username(), row.displayName(), row.bio(),
						mediaService.iconUrl(row.iconImageKey()), row.following(), row.followedBy()))
				.toList();
		return new FollowUsersResponse(users, nextBefore);
	}

}
