package io.github.keiwebcreate.raisetechtimeline.post.dto;

/**
 * 「新しい投稿 N件」の数。
 *
 * @param count 新しい、自分以外の投稿の数（PostService.NEW_COUNT_MAX で打ち止め）
 */
public record NewPostCountResponse(int count) {
}
