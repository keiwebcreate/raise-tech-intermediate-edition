package io.github.keiwebcreate.raisetechtimeline.comment.dto;

import java.util.List;

/**
 * 一番上のコメントの下に並ぶ返信の返事。
 *
 * @param replies 返信すべて（返信の返信も含む。1段にまとめて、古い順）
 */
public record RepliesResponse(List<CommentResponse> replies) {
}
