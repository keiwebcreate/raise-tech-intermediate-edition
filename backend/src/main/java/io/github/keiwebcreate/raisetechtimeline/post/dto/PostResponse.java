package io.github.keiwebcreate.raisetechtimeline.post.dto;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

import io.github.keiwebcreate.raisetechtimeline.post.Post;

/**
 * 投稿1件の返事。
 *
 * @param editedAt 編集した日時。編集していなければ null（画面の「編集済み」）
 * @param mine 見ている人の投稿か（画面は自分の投稿にだけ「…」メニューを出す）
 */
public record PostResponse(
		long id,
		String content,
		OffsetDateTime createdAt,
		@Nullable OffsetDateTime editedAt,
		Author author,
		boolean mine) {

	/** 投稿した人。メールアドレスは他人に見せないので入れない */
	public record Author(long id, String username, String displayName) {
	}

	public static PostResponse from(Post post, long viewerId) {
		Author author = new Author(post.userId(), post.authorUsername(), post.authorDisplayName());
		return new PostResponse(post.id(), post.content(), post.createdAt(), post.editedAt(), author,
				post.userId() == viewerId);
	}

}
