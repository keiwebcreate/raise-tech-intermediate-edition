package io.github.keiwebcreate.raisetechtimeline.user;

import org.jspecify.annotations.Nullable;

/**
 * ユーザー検索の結果の1人（users の1行と、見ている人から見たフォローの状態。docs/features/user-search.md 4.1）。
 *
 * @param bio 自己紹介。なければ null
 * @param iconImageKey アイコンの S3 のキー。未設定なら null
 * @param following 見ている人が、この人をフォローしているか
 * @param followedBy この人が、見ている人をフォローしているか（「フォローされています」）
 */
public record SearchedUser(
		long userId,
		String username,
		String displayName,
		@Nullable String bio,
		@Nullable String iconImageKey,
		boolean following,
		boolean followedBy) {
}
