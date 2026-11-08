package io.github.keiwebcreate.raisetechtimeline.common.error;

/**
 * リフレッシュトークンがない・期限切れ・使えなくなっている（401 REFRESH_INVALID）。
 * 画面はこれを受けたら、ログイン画面へ移す。
 */
public class RefreshTokenInvalidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RefreshTokenInvalidException(String reason) {
		super(reason);
	}

}
