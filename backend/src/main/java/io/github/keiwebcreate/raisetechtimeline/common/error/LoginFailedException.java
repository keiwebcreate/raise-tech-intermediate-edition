package io.github.keiwebcreate.raisetechtimeline.common.error;

/** ログインの失敗（401 LOGIN_FAILED）。メールアドレスとパスワードのどちらが違うかは教えない */
public class LoginFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LoginFailedException() {
		super("メールアドレスまたはパスワードが正しくありません");
	}

}
