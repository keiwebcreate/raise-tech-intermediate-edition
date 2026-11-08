package io.github.keiwebcreate.raisetechtimeline.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 今の時刻を返す時計。トークンの期限などは、この時計で決める。
 * Instant.now() を直接使わないのは、テストで時刻を動かせるようにするため。
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
