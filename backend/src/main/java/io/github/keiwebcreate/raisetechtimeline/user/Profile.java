package io.github.keiwebcreate.raisetechtimeline.user;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

/**
 * プロフィールに出すもの（users と、follows で数えた数。docs/features/profile.md 3.1）。
 * メールアドレス・パスワードは他人に見せないので、読まない。
 *
 * @param bio 自己紹介。なければ null
 * @param iconImageKey アイコンの S3 のキー。未設定なら null
 * @param createdAt 登録した日時（「2026年11月から利用しています」）
 * @param followingCount フォローしている人の数
 * @param followerCount フォローされている人の数
 */
public record Profile(
		long id,
		String username,
		String displayName,
		@Nullable String bio,
		@Nullable String iconImageKey,
		OffsetDateTime createdAt,
		long followingCount,
		long followerCount) {
}
