package io.github.keiwebcreate.raisetechtimeline.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.keiwebcreate.raisetechtimeline.common.error.RateLimitedException;

/**
 * ログインの失敗の回数制限（要件 N-23）。同じ接続元（IP アドレス）から、15分に10回まで。
 *
 * 失敗の記録はメモリに持つ簡単な形（サーバーを起動し直すと消える。サーバーは1台なので足りる）。
 * 本番では CloudFront・ALB を通るので、接続元の IP アドレスの取り方はデプロイのときに見直す。
 */
@Component
public class LoginRateLimiter {

	private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
	private final int maxFailures;
	private final Duration window;
	private final Clock clock;

	public LoginRateLimiter(AuthProperties properties, Clock clock) {
		this.maxFailures = properties.loginMaxFailures();
		this.window = properties.loginFailureWindow();
		this.clock = clock;
	}

	/**
	 * 失敗が上限に達していたら断る。
	 *
	 * @throws RateLimitedException 上限に達しているとき
	 */
	public void check(String clientIp) {
		Deque<Instant> times = failures.get(clientIp);
		if (times == null) {
			return;
		}
		synchronized (times) {
			Instant now = clock.instant();
			dropOld(times, now);
			if (times.size() >= maxFailures) {
				// いちばん古い失敗が窓の外に出るまでの秒数
				long wait = Duration.between(now, times.peekFirst().plus(window)).toSeconds() + 1;
				throw new RateLimitedException(wait);
			}
		}
	}

	public void recordFailure(String clientIp) {
		Deque<Instant> times = failures.computeIfAbsent(clientIp, ip -> new ArrayDeque<>());
		synchronized (times) {
			Instant now = clock.instant();
			dropOld(times, now);
			times.addLast(now);
		}
	}

	/** 古い記録を消して、メモリが増え続けないようにする（1時間ごと） */
	@Scheduled(fixedDelayString = "PT1H")
	public void purge() {
		Instant now = clock.instant();
		failures.entrySet().removeIf(entry -> {
			synchronized (entry.getValue()) {
				dropOld(entry.getValue(), now);
				return entry.getValue().isEmpty();
			}
		});
	}

	private void dropOld(Deque<Instant> times, Instant now) {
		Instant from = now.minus(window);
		while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
			times.pollFirst();
		}
	}

}
