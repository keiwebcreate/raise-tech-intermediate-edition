package io.github.keiwebcreate.raisetechtimeline.post;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

/**
 * 投稿1件と、投稿した人の名前（posts と users を JOIN した1行。docs/database-design.md 3.2）。
 * MyBatis が SQL の結果をこの record に入れる。API の返事には PostResponse に詰め替える。
 *
 * @param editedAt 編集した日時。編集していなければ null
 * @param likeCount いいね数（docs/features/like.md 4.2）
 * @param liked 見ている人がいいねしているか
 * @param commentCount コメント数（投稿に直接付いたものだけ。返信は数えない。docs/features/comment.md 4.3）
 */
public record Post(
		long id,
		long userId,
		String content,
		@Nullable OffsetDateTime editedAt,
		OffsetDateTime createdAt,
		String authorUsername,
		String authorDisplayName,
		long likeCount,
		boolean liked,
		long commentCount) {
}
