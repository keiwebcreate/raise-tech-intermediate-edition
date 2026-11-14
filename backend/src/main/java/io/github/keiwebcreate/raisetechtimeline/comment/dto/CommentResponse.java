package io.github.keiwebcreate.raisetechtimeline.comment.dto;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

import io.github.keiwebcreate.raisetechtimeline.comment.Comment;

/**
 * コメント・返信1件の返事。
 *
 * @param parentCommentId 返信先のコメント。投稿への直接のコメントなら null
 * @param rootCommentId 返信が並ぶ一番上のコメント。投稿への直接のコメントなら null
 * @param replyTo 返信先のコメントを書いた人。直接のコメントなら null
 *     （画面は、返信への返信のとき＝返信先が一番上のコメントでないときに「返信先：@ユーザー名」と出す）
 * @param mine 見ている人のコメントか（画面は自分のものにだけ「削除」を出す）
 * @param replyCount 直接付いた返信の数（「↩ 返信 N」）
 * @param threadReplyCount 一番上のコメントの下に並ぶ返信すべての数（「返信を表示（N件）」）。返信なら 0
 */
public record CommentResponse(
		long id,
		long postId,
		@Nullable Long parentCommentId,
		@Nullable Long rootCommentId,
		String content,
		OffsetDateTime createdAt,
		Author author,
		@Nullable ReplyTo replyTo,
		boolean mine,
		long likeCount,
		boolean liked,
		long replyCount,
		long threadReplyCount) {

	/**
	 * 書いた人。メールアドレスは他人に見せないので入れない。
	 *
	 * @param iconUrl アイコンを見るための期限付きの URL。未設定なら null
	 */
	public record Author(long id, String username, String displayName, @Nullable String iconUrl) {
	}

	/** 返信先のコメントを書いた人 */
	public record ReplyTo(String username) {
	}

	public static CommentResponse from(Comment comment, long viewerId, @Nullable String iconUrl) {
		Author author = new Author(comment.userId(), comment.authorUsername(), comment.authorDisplayName(),
				iconUrl);
		String replyToUsername = comment.replyToUsername();
		ReplyTo replyTo = replyToUsername == null ? null : new ReplyTo(replyToUsername);
		return new CommentResponse(comment.id(), comment.postId(), comment.parentCommentId(),
				comment.rootCommentId(), comment.content(), comment.createdAt(), author, replyTo,
				comment.userId() == viewerId, comment.likeCount(), comment.liked(), comment.replyCount(),
				comment.threadReplyCount());
	}

}
