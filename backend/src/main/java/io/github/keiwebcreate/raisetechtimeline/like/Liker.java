package io.github.keiwebcreate.raisetechtimeline.like;

import org.jspecify.annotations.Nullable;

/**
 * いいねした人1人（likes と users を JOIN した1行）。
 *
 * @param likeId いいねの ID（一覧の続きを読むときの目印）
 * @param userId いいねした人の ID
 * @param bio 自己紹介。なければ null
 * @param iconImageKey アイコンの S3 のキー。未設定なら null
 * @param following 見ている人が、この人をフォローしているか
 * @param followedBy この人が、見ている人をフォローしているか
 */
public record Liker(
		long likeId,
		long userId,
		String username,
		String displayName,
		@Nullable String bio,
		@Nullable String iconImageKey,
		boolean following,
		boolean followedBy) {
}
