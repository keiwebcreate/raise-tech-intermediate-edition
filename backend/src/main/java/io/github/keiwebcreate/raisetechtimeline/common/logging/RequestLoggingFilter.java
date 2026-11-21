package io.github.keiwebcreate.raisetechtimeline.common.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API 1回ごとに、リクエストの ID を振ってアクセスログを1行出す（docs/logging.md 3章・5章）。
 *
 * - 一番外側（Spring Security より前）で動かす。ログインで断られたときのログにも、同じ ID が付くようにするため
 * - ID は MDC（その間に出すログすべてに付く「付箋」）に貼り、返事のヘッダー X-Request-Id にも入れる
 * - URL は形（/api/posts/{id}）で出す。? から後ろ（検索の言葉など）は出さない
 * - 終わったら MDC を外す（同じスレッドで次に動く処理に、前のリクエストの ID が付かないように）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	private static final long NANOS_PER_MILLI = 1_000_000L;

	/** URL の形の中の「{名前:決まり}」 */
	private static final Pattern PATTERN_RULE = Pattern.compile("\\{(\\w+):[^}]*}");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// API だけ（Swagger の画面などは出さない）
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = UUID.randomUUID().toString();
		MDC.put(LogKeys.REQUEST_ID, requestId);
		response.setHeader(LogKeys.REQUEST_ID_HEADER, requestId);
		long start = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = (System.nanoTime() - start) / NANOS_PER_MILLI;
			String method = request.getMethod();
			String path = pathOf(request);
			int status = response.getStatus();
			log.atInfo()
					.addKeyValue("method", method)
					.addKeyValue("path", path)
					.addKeyValue("status", status)
					.addKeyValue("durationMs", durationMs)
					.log("{} {} {} {}ms", method, path, status, durationMs);
			MDC.remove(LogKeys.REQUEST_ID);
			MDC.remove(LogKeys.USER_ID);
		}
	}

	/** URL の形（/api/posts/{id}）。分からなければ（ない URL・ログインで断られた）、実際の URL（? より前） */
	private static String pathOf(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (pattern instanceof String text) {
			// {id:\d+}（数字だけの決まり）は {id} にする。ログで API ごとに数えやすくするため
			return PATTERN_RULE.matcher(text).replaceAll("{$1}");
		}
		return request.getRequestURI();
	}

}
