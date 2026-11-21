package io.github.keiwebcreate.raisetechtimeline.common.logging;

/**
 * ログの JSON に足す項目の名前（docs/logging.md 3章）。
 * MDC（その間に出すログすべてに付く「付箋」）と、1行だけに足す項目の両方で使う。
 */
public final class LogKeys {

	/** リクエストの ID。1回の API の間に出たログすべてに付く（MDC） */
	public static final String REQUEST_ID = "requestId";

	/** ログイン中の人の ID（MDC）。メールアドレスやユーザー名は使わない */
	public static final String USER_ID = "userId";

	/** 接続元の IP アドレス（ログイン・回数制限のログだけ） */
	public static final String CLIENT_IP = "clientIp";

	/** リクエストの ID を返す、返事のヘッダー */
	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	private LogKeys() {
	}

}
