package io.github.keiwebcreate.raisetechtimeline.common.error;

/**
 * 投稿が見つからない（404 POST_NOT_FOUND）。
 * 他人の投稿を編集・削除しようとしたときも、これにする（投稿があるかどうかも教えない。docs/features/post.md 2章）
 */
public class PostNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PostNotFoundException() {
		super("この投稿は見つかりません");
	}

}
