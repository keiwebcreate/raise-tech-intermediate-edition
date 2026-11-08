package io.github.keiwebcreate.raisetechtimeline.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * ログインの Cookie の作り方を1か所にまとめる（docs/features/authentication.md 7）。
 *
 * | Cookie        | 中身                     | Path        | 期限  |
 * | ACCESS_TOKEN  | アクセストークン（JWT）    | /           | 15分 |
 * | REFRESH_TOKEN | リフレッシュトークン       | /api/auth   | 7日  |
 *
 * - HttpOnly：画面の JavaScript から読めない（XSS で盗まれない。要件 N-27）
 * - SameSite=Lax：ほかのサイトからの POST には付かない（CSRF の対策の1つ）
 * - REFRESH_TOKEN は Path を /api/auth にして、ほかの API には送らない
 */
@Component
public class AuthCookies {

	public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
	public static final String REFRESH_TOKEN = "REFRESH_TOKEN";
	private static final String ACCESS_PATH = "/";
	private static final String REFRESH_PATH = "/api/auth";

	private final AuthProperties properties;

	public AuthCookies(AuthProperties properties) {
		this.properties = properties;
	}

	/** ログイン・取り替えのときに返す2つの Cookie */
	public List<ResponseCookie> issue(String accessToken, String refreshToken) {
		return List.of(
				cookie(ACCESS_TOKEN, accessToken, ACCESS_PATH, properties.accessTokenTtl()),
				cookie(REFRESH_TOKEN, refreshToken, REFRESH_PATH, properties.refreshTokenTtl()));
	}

	/** ログアウト・取り替えの失敗のときに、2つの Cookie を消す（期限 0 で上書きする） */
	public List<ResponseCookie> clear() {
		return List.of(
				cookie(ACCESS_TOKEN, "", ACCESS_PATH, Duration.ZERO),
				cookie(REFRESH_TOKEN, "", REFRESH_PATH, Duration.ZERO));
	}

	private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(properties.cookieSecure())
				.sameSite("Lax")
				.path(path)
				.maxAge(maxAge)
				.build();
	}

}
