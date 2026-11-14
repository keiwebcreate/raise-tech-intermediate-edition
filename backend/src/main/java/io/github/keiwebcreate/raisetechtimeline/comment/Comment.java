package io.github.keiwebcreate.raisetechtimeline.comment;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

/**
 * コメント・返信1件と、書いた人の名前、数（comments と users を JOIN した1行。docs/database-design.md 3.3）。
 * MyBatis が SQL の結果をこの record に入れる。API の返事には CommentResponse に詰め替える。
 *
 * @param parentCommentId 返信先のコメント。投稿への直接のコメントなら null
 * @param rootCommentId 返信が並ぶ一番上のコメント。投稿への直接のコメントなら null
 * @param authorIconImageKey 書いた人のアイコンの S3 のキー。未設定なら null
 * @param replyToUsername 返信先のコメントを書いた人の @ユーザー名。直接のコメントなら null
 * @param likeCount いいね数
 * @param liked 見ている人がいいねしているか
 * @param replyCount 直接付いた返信の数（「↩ 返信 N」。comment.md 4.3）
 * @param threadReplyCount 一番上のコメントの下に並ぶ返信すべての数（「返信を表示（N件）」）。返信なら 0
 */
public record Comment(
		long id,
		long postId,
		long userId,
		@Nullable Long parentCommentId,
		@Nullable Long rootCommentId,
		String content,
		OffsetDateTime createdAt,
		String authorUsername,
		String authorDisplayName,
		@Nullable String authorIconImageKey,
		@Nullable String replyToUsername,
		long likeCount,
		boolean liked,
		long replyCount,
		long threadReplyCount) {
}
