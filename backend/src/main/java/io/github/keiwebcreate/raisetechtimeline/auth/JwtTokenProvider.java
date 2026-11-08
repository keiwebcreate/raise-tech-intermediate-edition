package io.github.keiwebcreate.raisetechtimeline.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * アクセストークン（JWT）を作る・確かめる（jjwt を使う。講義と同じ道具）。
 *
 * - 中身：subject＝ユーザー ID、発行日時、期限（15分）。メールアドレスなどの個人の情報は入れない
 *   （JWT の中身は署名されているだけで、暗号化はされていない。だれでも読める）
 * - 署名：HMAC-SHA256。鍵は環境変数 SNS_JWT_SECRET（Base64）
 *
 * 鍵が短いとコンストラクタで止める（起動させない）ので、作りかけのオブジェクトを悪用されないよう final にする（SpotBugs の指摘）。
 */
@Component
public final class JwtTokenProvider {

	/** HMAC-SHA256 の鍵は 256 ビット（32 バイト）以上が必要 */
	private static final int MIN_KEY_BYTES = 32;

	private final SecretKey key;
	private final JwtParser parser;
	private final AuthProperties properties;
	private final Clock clock;

	public JwtTokenProvider(AuthProperties properties, Clock clock) {
		byte[] keyBytes = Decoders.BASE64.decode(properties.jwtSecret());
		if (keyBytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException("app.auth.jwt-secret は 32 バイト以上にしてください");
		}
		this.key = Keys.hmacShaKeyFor(keyBytes);
		this.properties = properties;
		this.clock = clock;
		this.parser = Jwts.parser()
				.verifyWith(key)
				// 期限の判定に、テストで動かせる Clock を使う
				.clock(() -> Date.from(clock.instant()))
				.build();
	}

	public String createAccessToken(long userId) {
		Instant now = clock.instant();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(properties.accessTokenTtl())))
				.signWith(key)
				.compact();
	}

	/**
	 * JWT を確かめて、ユーザー ID を返す。
	 *
	 * @throws ExpiredJwtException 期限が切れているとき
	 * @throws JwtException 署名が合わない・形が壊れているなど
	 */
	public long parseUserId(String token) {
		String subject = parser.parseSignedClaims(token).getPayload().getSubject();
		try {
			return Long.parseLong(subject);
		} catch (NumberFormatException e) {
			throw new JwtException("subject がユーザー ID ではありません", e);
		}
	}

}
