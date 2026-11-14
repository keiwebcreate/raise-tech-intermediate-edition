package io.github.keiwebcreate.raisetechtimeline.like;

import org.jspecify.annotations.Nullable;

/**
 * いいねした人1人（likes と users を JOIN した1行）。
 *
 * @param likeId いいねの ID（一覧の続きを読むときの目印）
 * @param userId いいねした人の ID
 * @param iconImageKey アイコンの S3 のキー。未設定なら null
 */
public record Liker(
		long likeId,
		long userId,
		String username,
		String displayName,
		@Nullable String iconImageKey) {
}
