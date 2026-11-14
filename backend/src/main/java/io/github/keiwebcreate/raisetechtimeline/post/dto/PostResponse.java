package io.github.keiwebcreate.raisetechtimeline.post.dto;

import java.time.OffsetDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.keiwebcreate.raisetechtimeline.post.Post;

/**
 * 投稿1件の返事。
 *
 * @param editedAt 編集した日時。編集していなければ null（画面の「編集済み」）
 * @param mine 見ている人の投稿か（画面は自分の投稿にだけ「…」メニューを出す）
 * @param media 画像・動画（並び順）。なければ空
 * @param likeCount いいね数
 * @param liked 見ている人がいいねしているか（画面はハートを赤く塗る）
 * @param commentCount コメント数（投稿に直接付いたものだけ。返信は数えない）
 */
public record PostResponse(
		long id,
		String content,
		OffsetDateTime createdAt,
		@Nullable OffsetDateTime editedAt,
		Author author,
		boolean mine,
		List<Media> media,
		long likeCount,
		boolean liked,
		long commentCount) {

	/**
	 * 投稿した人。メールアドレスは他人に見せないので入れない。
	 *
	 * @param iconUrl アイコンを見るための期限付きの URL。未設定なら null（画面は頭文字の丸を出す）
	 */
	public record Author(long id, String username, String displayName, @Nullable String iconUrl) {
	}

	/**
	 * 画像・動画1つ。
	 *
	 * @param type image か video
	 * @param key S3 のキー（編集で残すときに、そのまま送り返す）
	 * @param url 見るための期限付きの URL（期限は1時間）
	 */
	public record Media(String type, String key, String url) {
	}

	public static PostResponse from(Post post, long viewerId, List<Media> media, @Nullable String iconUrl) {
		Author author = new Author(post.userId(), post.authorUsername(), post.authorDisplayName(), iconUrl);
		return new PostResponse(post.id(), post.content(), post.createdAt(), post.editedAt(), author,
				post.userId() == viewerId, media, post.likeCount(), post.liked(),
				post.commentCount());
	}

}
