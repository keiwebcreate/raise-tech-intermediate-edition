package io.github.keiwebcreate.raisetechtimeline.common.error;

/**
 * ユーザーが見つからない（404 USER_NOT_FOUND。docs/features/profile.md 3.1・5章）。
 */
public class UserNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UserNotFoundException() {
		super("このアカウントは存在しません");
	}

}
