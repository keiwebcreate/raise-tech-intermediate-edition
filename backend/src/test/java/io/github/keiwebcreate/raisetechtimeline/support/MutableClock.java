package io.github.keiwebcreate.raisetechtimeline.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 単体テスト用の時計。今の時刻を自由に進められる（「15分たった」「7日たった」を待たずに試すため）。
 * 本物のコードは Clock を受け取るので（ClockConfig）、テストではこれを渡す。
 */
public final class MutableClock extends Clock {

	private Instant now;

	public MutableClock(Instant start) {
		this.now = start;
	}

	/** 時計を進める */
	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
