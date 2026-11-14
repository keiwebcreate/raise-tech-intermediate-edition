package io.github.keiwebcreate.raisetechtimeline.follow;

import org.jspecify.annotations.Nullable;

/**
 * フォロー中・フォロワーの一覧の1人（follows と users を JOIN した1行。docs/features/profile.md 4.5）。
 *
 * @param followId フォローの行の ID（一覧の続きを読むときの目印）
 * @param bio 自己紹介。なければ null
 * @param iconImageKey アイコンの S3 のキー。未設定なら null
 * @param following 見ている人が、この人をフォローしているか
 * @param followedBy この人が、見ている人をフォローしているか（「フォローされています」）
 */
public record FollowUser(
		long followId,
		long userId,
		String username,
		String displayName,
		@Nullable String bio,
		@Nullable String iconImageKey,
		boolean following,
		boolean followedBy) {
}
