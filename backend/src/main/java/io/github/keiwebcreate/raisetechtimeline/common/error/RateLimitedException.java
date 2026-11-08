package io.github.keiwebcreate.raisetechtimeline.common.error;

/** 回数制限を超えた（429 RATE_LIMITED。要件 N-23） */
public class RateLimitedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final long retryAfterSeconds;

	public RateLimitedException(long retryAfterSeconds) {
		super("しばらくしてから再度お試しください");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/** 何秒後に試せるか（レスポンスのヘッダー Retry-After） */
	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
