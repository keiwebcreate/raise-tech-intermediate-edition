package io.github.keiwebcreate.raisetechtimeline.common.error;

/** 登録済みのメールアドレス（409 EMAIL_DUPLICATED） */
public class EmailDuplicatedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EmailDuplicatedException() {
		super("このメールアドレスは登録済みです");
	}

}
