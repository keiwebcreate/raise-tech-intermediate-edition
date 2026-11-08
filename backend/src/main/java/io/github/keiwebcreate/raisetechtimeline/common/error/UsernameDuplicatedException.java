package io.github.keiwebcreate.raisetechtimeline.common.error;

/** 使われているユーザー名（409 USERNAME_DUPLICATED） */
public class UsernameDuplicatedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UsernameDuplicatedException() {
		super("このユーザー名は使われています");
	}

}
