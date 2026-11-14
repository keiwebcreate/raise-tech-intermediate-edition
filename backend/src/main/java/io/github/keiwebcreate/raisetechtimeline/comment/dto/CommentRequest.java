package io.github.keiwebcreate.raisetechtimeline.comment.dto;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;

/**
 * コメント・返信の入力（docs/features/comment.md 3章）。
 * 本文の文字数（前後の空白を取り除いて、絵文字も1文字として1〜280）は CommentService で確かめる。
 *
 * @param parentCommentId 返信先のコメント。null なら投稿への直接のコメント
 */
public record CommentRequest(
		@NotNull(message = "コメントを入力してください")
		String content,
		@Nullable Long parentCommentId) {
}
