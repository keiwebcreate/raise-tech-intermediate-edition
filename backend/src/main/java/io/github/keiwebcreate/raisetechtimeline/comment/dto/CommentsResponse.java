package io.github.keiwebcreate.raisetechtimeline.comment.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 投稿へのコメントの一覧の返事（S-04）。
 *
 * @param comments 古い順（50件まで）。返信は入らない（返信は /api/comments/{id}/replies で読む）
 * @param nextAfter 続きを読むときに after に入れる値。続きがなければ null
 */
public record CommentsResponse(List<CommentResponse> comments, @Nullable Long nextAfter) {
}
