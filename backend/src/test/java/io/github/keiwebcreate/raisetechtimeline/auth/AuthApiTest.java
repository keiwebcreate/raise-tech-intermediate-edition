package io.github.keiwebcreate.raisetechtimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;

/**
 * 認証の API のテスト（docs/features/authentication.md）。本物の PostgreSQL 18（Testcontainers）を使う。
 *
 * CSRF は、画面と同じく「GET で Cookie XSRF-TOKEN を受け取り、その値をヘッダー X-XSRF-TOKEN に付ける」形で送る。
 * ログイン失敗の回数制限は接続元の IP アドレスごとなので、テストごとに別の IP アドレスにする。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

	private static final String PASSWORD = "password123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${app.auth.jwt-secret}")
	private String jwtSecret;

	private final String clientIp = "10.0." + ThreadLocalRandom.current().nextInt(256) + "."
			+ ThreadLocalRandom.current().nextInt(256);

	// ===== 新規登録（A-01） =====

	@Test
	void 新規登録すると_201と本人の情報と2つのCookieが返る() throws Exception {
		String email = newEmail();
		String username = newUsername();
		MvcResult result = signup(email, username.toUpperCase(), "山田 太郎")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.username").value(username))
				.andExpect(jsonPath("$.displayName").value("山田 太郎"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();

		Cookie access = cookie(result, AuthCookies.ACCESS_TOKEN);
		assertThat(access.isHttpOnly()).isTrue();
		assertThat(access.getSecure()).isTrue();
		assertThat(access.getPath()).isEqualTo("/");
		assertThat(access.getMaxAge()).isEqualTo(15 * 60);
		Cookie refresh = cookie(result, AuthCookies.REFRESH_TOKEN);
		assertThat(refresh.isHttpOnly()).isTrue();
		assertThat(refresh.getPath()).isEqualTo("/api/auth");
		assertThat(refresh.getMaxAge()).isEqualTo(7 * 24 * 60 * 60);
		assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(h -> h.contains("SameSite=Lax"));

		// パスワードは BCrypt のハッシュで、リフレッシュトークンは SHA-256 のハッシュで保存される
		String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE email = ?",
				String.class, email);
		assertThat(passwordHash).startsWith("$2").isNotEqualTo(PASSWORD);
		Integer stored = jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
				Integer.class, RefreshTokenService.hash(refresh.getValue()));
		assertThat(stored).isEqualTo(1);
	}

	@Test
	void 登録済みのメールアドレスは_大文字違いでも登録できない() throws Exception {
		String email = newEmail();
		signup(email, newUsername(), "一人目").andExpect(status().isCreated());

		signup(email.toUpperCase(), newUsername(), "二人目")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"))
				.andExpect(jsonPath("$.detail").value("このメールアドレスは登録済みです"));
	}

	@Test
	void 使われているユーザー名は_大文字違いでも登録できない() throws Exception {
		String username = newUsername();
		signup(newEmail(), username, "一人目").andExpect(status().isCreated());

		signup(newEmail(), username.toUpperCase(), "二人目")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USERNAME_DUPLICATED"))
				.andExpect(jsonPath("$.detail").value("このユーザー名は使われています"));
	}

	@Test
	void 入力のルールに合わないと_400と項目ごとの文言が返る() throws Exception {
		String json = """
				{"email": "not-an-email", "password": "short", "username": "a!", "displayName": " "}
				""";
		perform(post("/api/auth/signup"), json)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("メールアドレスの形式で入力してください"))
				.andExpect(jsonPath("$.errors[?(@.field == 'password')].message")
						.value("パスワードは8〜64文字の半角英数字・記号で入力してください"))
				.andExpect(jsonPath("$.errors[?(@.field == 'username')].message")
						.value("ユーザー名は3〜15文字の半角英数字と _ で入力してください"))
				.andExpect(jsonPath("$.errors[?(@.field == 'displayName')].message").value("表示名を入力してください"));
	}

	@Test
	void 表示名は絵文字も1文字と数え_50文字までなら登録でき_51文字はできない() throws Exception {
		signup(newEmail(), newUsername(), "😀".repeat(50)).andExpect(status().isCreated());

		signup(newEmail(), newUsername(), "😀".repeat(51))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("displayName"))
				.andExpect(jsonPath("$.errors[0].message").value("表示名は50文字以内で入力してください"));
	}

	// ===== ログイン（A-02） =====

	@Test
	void 正しいメールアドレスとパスワードでログインでき_meで本人がわかる() throws Exception {
		String email = newEmail();
		signup(email, newUsername(), "ログイン太郎").andExpect(status().isCreated());

		MvcResult result = login(email.toUpperCase(), PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andReturn();

		mockMvc.perform(get("/api/auth/me").cookie(cookie(result, AuthCookies.ACCESS_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.displayName").value("ログイン太郎"));
	}

	@Test
	void パスワード違いと_登録されていないメールアドレスは_同じ文言で断る() throws Exception {
		String email = newEmail();
		signup(email, newUsername(), "太郎").andExpect(status().isCreated());

		for (String target : List.of(email, newEmail())) {
			login(target, "wrong-password")
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
					.andExpect(jsonPath("$.detail").value("メールアドレスまたはパスワードが正しくありません"));
		}
	}

	@Test
	void 同じ接続元からログインに10回失敗すると_11回目は正しいパスワードでも429になる() throws Exception {
		String email = newEmail();
		signup(email, newUsername(), "太郎").andExpect(status().isCreated());
		for (int i = 0; i < 10; i++) {
			login(email, "wrong-password").andExpect(status().isUnauthorized());
		}

		login(email, PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.detail").value("しばらくしてから再度お試しください"));
	}

	// ===== ログインしていない人（A-04） =====

	@Test
	void アクセストークンがない_壊れていると_meは401になる() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, "broken.token.value")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 期限切れのアクセストークンは_401のTOKEN_EXPIREDになる() throws Exception {
		Instant past = Instant.now().minusSeconds(3600);
		String expired = Jwts.builder()
				.subject("1")
				.issuedAt(Date.from(past))
				.expiration(Date.from(past.plusSeconds(900)))
				.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
				.compact();

		mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, expired)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void ほかの鍵で署名したアクセストークンは使えない() throws Exception {
		String forged = Jwts.builder()
				.subject("1")
				.expiration(Date.from(Instant.now().plusSeconds(900)))
				.signWith(Keys.hmacShaKeyFor("another-secret-key-another-secret-key!!".getBytes()))
				.compact();

		mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS_TOKEN, forged)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	// ===== リフレッシュトークン =====

	@Test
	void リフレッシュすると新しい2つのトークンが出て_古いリフレッシュトークンは使えない() throws Exception {
		MvcResult signedUp = signup(newEmail(), newUsername(), "太郎").andReturn();
		Cookie oldRefresh = cookie(signedUp, AuthCookies.REFRESH_TOKEN);

		MvcResult refreshed = refresh(oldRefresh).andExpect(status().isOk()).andReturn();
		Cookie newAccess = cookie(refreshed, AuthCookies.ACCESS_TOKEN);
		Cookie newRefresh = cookie(refreshed, AuthCookies.REFRESH_TOKEN);
		assertThat(newRefresh.getValue()).isNotEqualTo(oldRefresh.getValue());
		mockMvc.perform(get("/api/auth/me").cookie(newAccess)).andExpect(status().isOk());

		// 新しいリフレッシュトークンは使える
		MvcResult again = refresh(newRefresh).andExpect(status().isOk()).andReturn();
		assertThat(cookie(again, AuthCookies.REFRESH_TOKEN).getValue()).isNotEqualTo(newRefresh.getValue());
	}

	@Test
	void 使用済みのリフレッシュトークンが使われると_その人のトークンがすべて取り消される() throws Exception {
		MvcResult signedUp = signup(newEmail(), newUsername(), "太郎").andReturn();
		Cookie stolen = cookie(signedUp, AuthCookies.REFRESH_TOKEN);
		Cookie legit = cookie(refresh(stolen).andExpect(status().isOk()).andReturn(), AuthCookies.REFRESH_TOKEN);

		// 盗まれた古いトークンが使われた
		MvcResult reused = refresh(stolen)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_INVALID"))
				.andReturn();
		// 2つの Cookie を消す指示が返る
		assertThat(cookie(reused, AuthCookies.ACCESS_TOKEN).getMaxAge()).isZero();
		assertThat(cookie(reused, AuthCookies.REFRESH_TOKEN).getMaxAge()).isZero();

		// 本人の新しいトークンも、もう使えない（もう一度ログインしてもらう）
		refresh(legit).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
	}

	@Test
	void 期限の切れたリフレッシュトークンと_知らないトークンと_Cookieなしは401になる() throws Exception {
		MvcResult signedUp = signup(newEmail(), newUsername(), "太郎").andReturn();
		Cookie refresh = cookie(signedUp, AuthCookies.REFRESH_TOKEN);
		jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
				RefreshTokenService.hash(refresh.getValue()));

		refresh(refresh).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
		refresh(new Cookie(AuthCookies.REFRESH_TOKEN, "unknown-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
		perform(post("/api/auth/refresh"), null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
	}

	// ===== ログアウト（A-03） =====

	@Test
	void ログアウトすると_リフレッシュトークンが取り消され_2つのCookieが消える() throws Exception {
		MvcResult signedUp = signup(newEmail(), newUsername(), "太郎").andReturn();
		Cookie refresh = cookie(signedUp, AuthCookies.REFRESH_TOKEN);

		MvcResult loggedOut = perform(post("/api/auth/logout"), null, refresh)
				.andExpect(status().isNoContent())
				.andReturn();
		assertThat(cookie(loggedOut, AuthCookies.ACCESS_TOKEN).getMaxAge()).isZero();
		assertThat(cookie(loggedOut, AuthCookies.REFRESH_TOKEN).getMaxAge()).isZero();
		String revokedAt = jdbcTemplate.queryForObject(
				"SELECT revoked_at::text FROM refresh_tokens WHERE token_hash = ?", String.class,
				RefreshTokenService.hash(refresh.getValue()));
		assertThat(revokedAt).isNotNull();

		refresh(refresh).andExpect(status().isUnauthorized());
	}

	// ===== CSRF =====

	@Test
	void CSRFトークンを付けないPOSTは403になる() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"a@example.com\", \"password\": \"password123\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	// ===== 道具 =====

	private ResultActions signup(String email, String username, String displayName) throws Exception {
		String json = """
				{"email": "%s", "password": "%s", "username": "%s", "displayName": "%s"}
				""".formatted(email, PASSWORD, username, displayName);
		return perform(post("/api/auth/signup"), json);
	}

	private ResultActions login(String email, String password) throws Exception {
		String json = """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
		return perform(post("/api/auth/login"), json);
	}

	private ResultActions refresh(Cookie refreshToken) throws Exception {
		return perform(post("/api/auth/refresh"), null, refreshToken);
	}

	/** 画面と同じ形で CSRF トークンを付けて送る */
	private ResultActions perform(MockHttpServletRequestBuilder builder, String json, Cookie... cookies)
			throws Exception {
		Cookie csrf = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).as("Cookie XSRF-TOKEN").isNotNull();
		builder.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
				.with(request -> {
					request.setRemoteAddr(clientIp);
					return request;
				});
		if (cookies.length > 0) {
			builder.cookie(cookies);
		}
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(builder);
	}

	private static Cookie cookie(MvcResult result, String name) {
		MockHttpServletResponse response = result.getResponse();
		Cookie cookie = response.getCookie(name);
		assertThat(cookie).as("Cookie " + name).isNotNull();
		return cookie;
	}

	private static String newEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String newUsername() {
		return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

}
