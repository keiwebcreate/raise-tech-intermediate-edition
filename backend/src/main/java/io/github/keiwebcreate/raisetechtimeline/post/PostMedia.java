package io.github.keiwebcreate.raisetechtimeline.post;

/**
 * 投稿の画像・動画1つ（post_media の1行。docs/database-design.md 3.2.1）。
 *
 * @param mediaType image か video
 * @param s3Key S3 のキー（posts/年/月/ランダム.拡張子）
 * @param position 並び順（1〜4）
 */
public record PostMedia(
		long postId,
		String mediaType,
		String s3Key,
		int position) {
}
