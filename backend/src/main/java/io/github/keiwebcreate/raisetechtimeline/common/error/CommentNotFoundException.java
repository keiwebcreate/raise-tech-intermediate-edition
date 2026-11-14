package io.github.keiwebcreate.raisetechtimeline.common.error;

/**
 * コメント・返信が見つからない（404 COMMENT_NOT_FOUND）。
 * 他人のコメントを消そうとしたときも、これにする（コメントがあるかどうかも教えない。docs/features/comment.md 2章）
 */
public class CommentNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** 返信しようとしたら、返信先のコメントが消されていた（comment.md 5章） */
	public static final String MESSAGE_PARENT_DELETED = "返信先のコメントは削除されました";

	private static final String MESSAGE_DEFAULT = "このコメントは見つかりません";

	public CommentNotFoundException() {
		super(MESSAGE_DEFAULT);
	}

	public CommentNotFoundException(String message) {
		super(message);
	}

}
