package io.github.keiwebcreate.raisetechtimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keiwebcreate.raisetechtimeline.common.error.RateLimitedException;
import io.github.keiwebcreate.raisetechtimeline.support.MutableClock;

/**
 * LoginRateLimiter の単体テスト（要件 N-23・docs/features/authentication.md 3.2）。
 * 同じ接続元からのログインの失敗は、15分に10回まで。11回目からは断る。
 */
class LoginRateLimiterTest {

	private static final String IP = "203.0.113.5";

	private MutableClock clock;

	private LoginRateLimiter limiter;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-11-21T03:00:00Z"));
		AuthProperties properties = new AuthProperties("not-used", Duration.ofMinutes(15), Duration.ofDays(7), true, 10,
				Duration.ofMinutes(15));
		limiter = new LoginRateLimiter(properties, clock);
	}

	@Test
	void 失敗が9回までなら_まだ試せる() {
		fail(9);
		assertThatCode(() -> limiter.check(IP)).doesNotThrowAnyException();
	}

	@Test
	void 失敗が10回たまったら_次は断り_言われた秒数だけ待てば試せる() {
		fail(10);

		RateLimitedException e = catchThrowableOfType(RateLimitedException.class, () -> limiter.check(IP));
		assertThat(e).isNotNull();
		// 秒の端数は切り上げるので、15分（900秒）か、それより少しだけ長い
		long wait = e.getRetryAfterSeconds();
		assertThat(wait).isBetween(15 * 60L, 15 * 60L + 1);

		// 言われた秒数より前は、まだ断る
		clock.advance(Duration.ofSeconds(wait - 2));
		assertThat(catchThrowableOfType(RateLimitedException.class, () -> limiter.check(IP))).isNotNull();
		// 言われた秒数を待てば、試せる（画面は Retry-After の秒数を待って、もう一度試せばよい）
		clock.advance(Duration.ofSeconds(2));
		assertThatCode(() -> limiter.check(IP)).doesNotThrowAnyException();
	}

	@Test
	void 最初の失敗から15分たてば_また試せる() {
		fail(10);
		clock.advance(Duration.ofMinutes(15).plusSeconds(1));

		assertThatCode(() -> limiter.check(IP)).doesNotThrowAnyException();
	}

	@Test
	void 数えるのは15分の中の失敗だけ_古い失敗から順に外れる() {
		fail(5);
		clock.advance(Duration.ofMinutes(10));
		fail(5);
		// 10回たまったので断る
		assertThat(catchThrowableOfType(RateLimitedException.class, () -> limiter.check(IP))).isNotNull();

		// 最初の5回が15分を過ぎると、残りは5回なので試せる
		clock.advance(Duration.ofMinutes(5).plusSeconds(1));
		assertThatCode(() -> limiter.check(IP)).doesNotThrowAnyException();
	}

	@Test
	void 接続元ごとに数える() {
		fail(10);
		assertThatCode(() -> limiter.check("198.51.100.7")).doesNotThrowAnyException();
	}

	@Test
	void 片付けても_15分の中の失敗は残る() {
		fail(10);
		limiter.purge();
		assertThat(catchThrowableOfType(RateLimitedException.class, () -> limiter.check(IP))).isNotNull();
	}

	private void fail(int times) {
		for (int i = 0; i < times; i++) {
			limiter.recordFailure(IP);
		}
	}

}
