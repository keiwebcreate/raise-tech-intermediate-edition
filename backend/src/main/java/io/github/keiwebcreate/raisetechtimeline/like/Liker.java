package io.github.keiwebcreate.raisetechtimeline.like;

/**
 * いいねした人1人（likes と users を JOIN した1行）。
 *
 * @param likeId いいねの ID（一覧の続きを読むときの目印）
 * @param userId いいねした人の ID
 */
public record Liker(
		long likeId,
		long userId,
		String username,
		String displayName) {
}
