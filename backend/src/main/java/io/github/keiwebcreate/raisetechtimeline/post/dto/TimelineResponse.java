package io.github.keiwebcreate.raisetechtimeline.post.dto;

import java.util.List;

/**
 * タイムラインの返事。
 *
 * @param posts 新しい順の投稿（20件まで）
 * @param hasMore まだ続きがあるか（画面はこれが false になったら、続きの読み込みをやめる）
 */
public record TimelineResponse(List<PostResponse> posts, boolean hasMore) {
}
