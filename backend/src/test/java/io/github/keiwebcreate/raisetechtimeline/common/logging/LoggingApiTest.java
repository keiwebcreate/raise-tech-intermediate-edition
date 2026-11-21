package io.github.keiwebcreate.raisetechtimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;
import io.github.keiwebcreate.raisetechtimeline.auth.AuthCookies;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ログの形と中身のテスト（docs/logging.md）。
 *
 * ログは標準出力に1行ずつ JSON（ECS）で出る。OutputCaptureExtension で出力を受け取り、1行ずつ JSON として読む。
 * ほかのテストのログも混ざるので、自分のリクエストの ID や、ランダムな目印で自分の行を探す。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LoggingApiTest {

	private static final String PASSWORD = "password123";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	private final String clientIp = "10.1." + ThreadLocalRandom.current().nextInt(256) + "."
			+ ThreadLocalRandom.current().nextInt(256);

	// ===== 形（2章・3章） =====

	@Test
	void ログは1行ずつJSONのECSの形で_テストの環境の名前が入る(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/api/auth/me"));

		List<JsonNode> lines = jsonLines(output);
		assertThat(lines).isNotEmpty();
		JsonNode line = lines.getLast();
		assertThat(line.path("@timestamp").isMissingNode()).isFalse();
		assertThat(line.path("log").path("level").asString()).isNotEmpty();
		assertThat(line.path("ecs").path("version").asString()).isNotEmpty();
		assertThat(line.path("service").path("name").asString()).isEqualTo("raisetechtimeline");
		assertThat(line.path("service").path("environment").asString()).isEqualTo("test");
	}

	// ===== アクセスログとリクエストの ID（3章・5章） =====

	@Test
	void APIを呼ぶとINFOのアクセスログが1行出て_URLの形と番号と時間と利用者のIDが入る(CapturedOutput output)
			throws Exception {
		Cookie access = signupAndGetAccessToken();

		MockHttpServletResponse response = mockMvc.perform(get("/api/posts/{id}", Long.MAX_VALUE).cookie(access))
				.andReturn().getResponse();

		String requestId = response.getHeader("X-Request-Id");
		JsonNode log = accessLogOf(output, requestId);
		assertThat(log.path("log").path("level").asString()).isEqualTo("INFO");
		assertThat(log.path("method").asString()).isEqualTo("GET");
		assertThat(log.path("path").asString()).isEqualTo("/api/posts/{id}");
		assertThat(log.path("status").asString()).isEqualTo("404");
		assertThat(log.path("durationMs").asString()).matches("\\d+");
		assertThat(log.path("userId").asString()).matches("\\d+");
		assertThat(log.path("message").asString()).isEqualTo(
				"GET /api/posts/{id} 404 " + log.path("durationMs").asString() + "ms");
	}

	@Test
	void 返事のヘッダーX_Request_IdにリクエストのIDが入り_リクエストごとに違う() throws Exception {
		String first = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getHeader("X-Request-Id");
		String second = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getHeader("X-Request-Id");

		assertThat(first).isNotBlank();
		assertThat(second).isNotBlank().isNotEqualTo(first);
	}

	@Test
	void ログインしていなければ_利用者のIDは付かない(CapturedOutput output) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse();

		JsonNode log = accessLogOf(output, response.getHeader("X-Request-Id"));
		assertThat(log.path("status").asString()).isEqualTo("401");
		assertThat(log.path("path").asString()).isEqualTo("/api/auth/me");
		assertThat(log.has("userId")).isFalse();
	}

	@Test
	void URLの疑問符から後ろ_検索の言葉は_ログに出さない(CapturedOutput output) throws Exception {
		Cookie access = signupAndGetAccessToken();
		String secret = "secret" + UUID.randomUUID().toString().replace("-", "");

		MockHttpServletResponse response = mockMvc.perform(get("/api/users").param("q", secret).cookie(access))
				.andReturn().getResponse();

		JsonNode log = accessLogOf(output, response.getHeader("X-Request-Id"));
		assertThat(log.path("path").asString()).isEqualTo("/api/users");
		assertThat(logText(output)).doesNotContain(secret);
	}

	@Test
	void リクエストが終わったら_ログの付箋_MDCからリクエストのIDと利用者のIDを外す() throws Exception {
		Cookie access = signupAndGetAccessToken();

		mockMvc.perform(get("/api/posts/{id}", Long.MAX_VALUE).cookie(access));

		// 同じスレッドで次に動く処理のログに、前のリクエストの ID が付かないように
		assertThat(MDC.get("requestId")).isNull();
		assertThat(MDC.get("userId")).isNull();
	}

	@Test
	void API以外の住所にログイン中のCookieで来ても_利用者のIDを付箋に残さない() throws Exception {
		Cookie access = signupAndGetAccessToken();

		// API 以外の住所でも、ログインの確かめ（JwtAuthenticationFilter）は動く
		mockMvc.perform(get("/not-api").cookie(access));

		// 残っていると、同じスレッドで次に動く別の人のリクエストのログに、この人の ID が付いてしまう
		assertThat(MDC.get("userId")).isNull();
	}

	// ===== 安全に関わる出来事（5章）と、出さないもの（6章） =====

	@Test
	void ログインに失敗したらWARNで接続元が出て_メールアドレスとパスワードは出ない(CapturedOutput output)
			throws Exception {
		String email = newEmail();
		String wrongPassword = "wrong-" + UUID.randomUUID();

		MockHttpServletResponse response = login(email, wrongPassword).andReturn().getResponse();

		String requestId = response.getHeader("X-Request-Id");
		JsonNode warn = findLine(output, line -> requestId.equals(line.path("requestId").asString())
				&& "WARN".equals(line.path("log").path("level").asString()));
		assertThat(warn.path("message").asString()).isEqualTo("ログインに失敗しました");
		assertThat(warn.path("clientIp").asString()).isEqualTo(clientIp);
		assertThat(logText(output)).doesNotContain(email).doesNotContain(wrongPassword);
	}

	@Test
	void ログインの失敗が多すぎて断ったら_WARNで出る(CapturedOutput output) throws Exception {
		String email = newEmail();
		for (int i = 0; i < 10; i++) {
			login(email, "wrong-password");
		}

		MockHttpServletResponse response = login(email, "wrong-password").andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(429);
		String requestId = response.getHeader("X-Request-Id");
		JsonNode warn = findLine(output, line -> requestId.equals(line.path("requestId").asString())
				&& "WARN".equals(line.path("log").path("level").asString()));
		assertThat(warn.path("message").asString()).isEqualTo("ログインの失敗が多すぎるため断りました");
		assertThat(warn.path("clientIp").asString()).isEqualTo(clientIp);
	}

	@Test
	void 新規登録しても_メールアドレス_パスワード_トークンはログに出ない(CapturedOutput output) throws Exception {
		String email = newEmail();

		MockHttpServletResponse response = signup(email).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(201);
		String accessToken = cookie(response, AuthCookies.ACCESS_TOKEN).getValue();
		String refreshToken = cookie(response, AuthCookies.REFRESH_TOKEN).getValue();
		assertThat(logText(output))
				.doesNotContain(email)
				.doesNotContain(PASSWORD)
				.doesNotContain(accessToken)
				.doesNotContain(refreshToken);
	}

	// ===== 道具 =====

	/** 出力のうち、JSON として読める行だけ（Spring Boot が起動するときの絵文字の行などは飛ばす） */
	private static List<JsonNode> jsonLines(CapturedOutput output) {
		List<JsonNode> lines = new ArrayList<>();
		for (String text : output.getOut().split("\\R")) {
			if (!text.startsWith("{")) {
				continue;
			}
			lines.add(JSON.readTree(text));
		}
		return lines;
	}

	/**
	 * アプリのログ（JSON の行）だけをつないだ文字。出してはいけない値が入っていないかを見るのに使う。
	 * MockMvc はテストが失敗すると送った中身を表示するので、出力すべて（getAll）では見ない
	 */
	private static String logText(CapturedOutput output) {
		return String.join("\n", output.getOut().lines().filter(text -> text.startsWith("{")).toList());
	}

	private static JsonNode findLine(CapturedOutput output, Predicate<JsonNode> condition) {
		return jsonLines(output).stream().filter(condition).findFirst()
				.orElseThrow(() -> new AssertionError("条件に合うログの行がない：\n" + output.getOut()));
	}

	/** そのリクエストのアクセスログ（RequestLoggingFilter が出す行） */
	private static JsonNode accessLogOf(CapturedOutput output, String requestId) {
		assertThat(requestId).as("X-Request-Id").isNotBlank();
		return findLine(output, line -> requestId.equals(line.path("requestId").asString())
				&& line.path("log").path("logger").asString().endsWith("RequestLoggingFilter"));
	}

	private Cookie signupAndGetAccessToken() throws Exception {
		return cookie(signup(newEmail()).andReturn().getResponse(), AuthCookies.ACCESS_TOKEN);
	}

	private ResultActions signup(String email) throws Exception {
		String username = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String json = """
				{"email": "%s", "password": "%s", "username": "%s", "displayName": "ログ 確認"}
				""".formatted(email, PASSWORD, username);
		return perform(post("/api/auth/signup"), json);
	}

	private ResultActions login(String email, String password)
			throws Exception {
		String json = """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
		return perform(post("/api/auth/login"), json);
	}

	/** 画面と同じ形で CSRF トークンを付けて送る（AuthApiTest と同じ） */
	private ResultActions perform(MockHttpServletRequestBuilder builder,
			String json) throws Exception {
		Cookie csrf = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).as("Cookie XSRF-TOKEN").isNotNull();
		builder.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
				.with(request -> {
					request.setRemoteAddr(clientIp);
					return request;
				});
		builder.contentType(MediaType.APPLICATION_JSON).content(json);
		return mockMvc.perform(builder);
	}

	private static Cookie cookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		assertThat(cookie).as("Cookie " + name).isNotNull();
		return cookie;
	}

	private static String newEmail() {
		return "log-" + UUID.randomUUID() + "@example.com";
	}

}
