package io.github.keiwebcreate.raisetechtimeline.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ログインの決まり（application.yml の app.auth）。
 *
 * @param jwtSecret JWT に署名する鍵（Base64。32バイト以上）。環境変数 SNS_JWT_SECRET から読む
 * @param accessTokenTtl アクセストークンの期限（15分）
 * @param refreshTokenTtl リフレッシュトークンの期限（7日）
 * @param cookieSecure Cookie に Secure を付けるか
 * @param loginMaxFailures 同じ接続元からのログインの失敗を、何回まで許すか
 * @param loginFailureWindow 失敗を数える時間の幅
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
		String jwtSecret,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		boolean cookieSecure,
		int loginMaxFailures,
		Duration loginFailureWindow) {
}
