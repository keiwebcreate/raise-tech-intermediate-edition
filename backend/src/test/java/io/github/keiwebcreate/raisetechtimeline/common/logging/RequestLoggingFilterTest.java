package io.github.keiwebcreate.raisetechtimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;

/**
 * RequestLoggingFilter の単体テスト（docs/logging.md 5章）。Spring を起動せず、フィルターだけを動かす。
 * ログは Logback の ListAppender（出たログを溜めておく箱）で受け取る。
 */
class RequestLoggingFilterTest {

	private final RequestLoggingFilter filter = new RequestLoggingFilter();

	private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void setUp() {
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
		MDC.clear();
	}

	@Test
	void 例外が外まで抜けたら_アクセスログの番号は500にする() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/1");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain failing = (req, res) -> {
			throw new IllegalStateException("フィルターの中の想定外の失敗");
		};

		// 例外は飲み込まず、そのまま外へ投げる（Tomcat が 500 の返事を作る）
		assertThatThrownBy(() -> filter.doFilter(request, response, failing))
				.isInstanceOf(IllegalStateException.class);

		ILoggingEvent log = appender.list.getLast();
		assertThat(keyValues(log)).containsEntry("status", "500");
		assertThat(log.getFormattedMessage()).startsWith("GET /api/posts/1 500 ");
		assertThat(MDC.get(LogKeys.REQUEST_ID)).isNull();
	}

	@Test
	void ふつうに終われば_返事の番号をそのまま出す() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/1");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(404));

		assertThat(keyValues(appender.list.getLast())).containsEntry("status", "404");
	}

	@Test
	void API以外の住所では_アクセスログを出さない() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui.html");

		filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
		});

		assertThat(appender.list).isEmpty();
	}

	private static Map<String, String> keyValues(ILoggingEvent event) {
		return event.getKeyValuePairs().stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
	}

}
