package io.github.keiwebcreate.raisetechtimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keiwebcreate.raisetechtimeline.support.MutableClock;
import io.jsonwebtoken.JwtException;

/**
 * JwtTokenProvider の単体テスト（docs/features/authentication.md 7.1・7.5）。
 * アクセストークン（JWT）は HS256 で署名し、期限は15分。鍵は Base64 で32バイト以上。
 */
class JwtTokenProviderTest {

	private static final String SECRET = base64("unit-test-key-for-raisetechtimeline-jwt-hs256");

	private MutableClock clock;

	private JwtTokenProvider provider;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-11-21T03:00:00Z"));
		provider = new JwtTokenProvider(properties(SECRET), clock);
	}

	@Test
	void 作ったトークンから_ユーザーIDを読める() {
		assertThat(provider.parseUserId(provider.createAccessToken(42L))).isEqualTo(42L);
	}

	@Test
	void 署名の方式はHS256() {
		String header = new String(Base64.getUrlDecoder().decode(provider.createAccessToken(42L).split("\\.")[0]),
				StandardCharsets.UTF_8);
		assertThat(header).contains("\"HS256\"");
	}

	@Test
	void 期限の15分を過ぎたら読めない() {
		String token = provider.createAccessToken(42L);
		clock.advance(Duration.ofMinutes(14));
		assertThat(provider.parseUserId(token)).isEqualTo(42L);

		clock.advance(Duration.ofMinutes(1).plusSeconds(1));
		assertThatThrownBy(() -> provider.parseUserId(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void 書き換えたトークン_別の鍵で作ったトークンは読めない() {
		String token = provider.createAccessToken(42L);
		String[] parts = token.split("\\.");
		// 中身（ユーザー ID）を 1 に書き換えた偽物
		String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"sub\":\"1\"}".getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
		assertThatThrownBy(() -> provider.parseUserId(forged)).isInstanceOf(JwtException.class);

		JwtTokenProvider other = new JwtTokenProvider(properties(base64("another-key-for-raisetechtimeline-jwt-hs256!")),
				clock);
		assertThatThrownBy(() -> provider.parseUserId(other.createAccessToken(42L))).isInstanceOf(JwtException.class);
	}

	@Test
	void 鍵が32バイトより短ければ_起動しない() {
		assertThatThrownBy(() -> new JwtTokenProvider(properties(base64("too-short-key-31-bytes-long!!!!")), clock))
				.isInstanceOf(RuntimeException.class);
	}

	private static AuthProperties properties(String secret) {
		return new AuthProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7), true, 10, Duration.ofMinutes(15));
	}

	private static String base64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

}
