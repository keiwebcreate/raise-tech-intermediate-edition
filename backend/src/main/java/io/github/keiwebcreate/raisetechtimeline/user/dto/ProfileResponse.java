package io.github.keiwebcreate.raisetechtimeline.user.dto;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

/**
 * プロフィールの返事（S-05。docs/features/profile.md 3.1）。メールアドレスは入れない。
 *
 * @param bio 自己紹介。なければ null
 * @param iconUrl アイコンを見るための期限付きの URL。未設定なら null（画面は頭文字の丸を出す）
 * @param iconKey アイコンの S3 のキー。未設定なら null（編集で「残す」ときに、そのまま送り返す）
 * @param createdAt 登録した日時（画面は「2026年11月から利用しています」と出す）
 * @param followingCount フォローしている人の数
 * @param followerCount フォローされている人の数
 * @param mine 見ている人のプロフィールか（画面は「プロフィールを編集」を出す）
 */
public record ProfileResponse(
		long id,
		String username,
		String displayName,
		@Nullable String bio,
		@Nullable String iconUrl,
		@Nullable String iconKey,
		OffsetDateTime createdAt,
		long followingCount,
		long followerCount,
		boolean mine) {
}
