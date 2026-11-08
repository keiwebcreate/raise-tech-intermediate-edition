package io.github.keiwebcreate.raisetechtimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;
import io.github.keiwebcreate.raisetechtimeline.auth.AuthCookies;
import io.github.keiwebcreate.raisetechtimeline.auth.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import net.minidev.json.JSONValue;

/**
 * 投稿とタイムラインの API のテスト（docs/features/post.md・timeline.md）。本物の PostgreSQL 18（Testcontainers）を使う。
 *
 * ユーザーは DB に直接作り、ログインの JWT は JwtTokenProvider で作る（ログインの回数制限などに左右されないように）。
 * DB はほかのテストと共有なので、並び順・件数は「フォロー中」タブ（自分とフォローした人だけ）で確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	// ===== 投稿する（P-01） =====

	@Test
	void 投稿すると_201と投稿が返り_前後の空白は取り除かれる() throws Exception {
		long me = newUser("山田 太郎");
		as(me, post("/api/posts"), json("  \n今日は Spring Boot を勉強した。\n2行目  \n"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("今日は Spring Boot を勉強した。\n2行目"))
				.andExpect(jsonPath("$.author.id").value(me))
				.andExpect(jsonPath("$.author.displayName").value("山田 太郎"))
				.andExpect(jsonPath("$.author.email").doesNotExist())
				.andExpect(jsonPath("$.editedAt").isEmpty())
				.andExpect(jsonPath("$.mine").value(true));
	}

	@Test
	void 絵文字も1文字と数え_280文字までは投稿でき_281文字は400() throws Exception {
		long me = newUser("絵文字");
		as(me, post("/api/posts"), json("😀".repeat(280))).andExpect(status().isCreated());
		as(me, post("/api/posts"), json("あ".repeat(281)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("content"))
				.andExpect(jsonPath("$.errors[0].message").value("280文字以内で入力してください"));
	}

	@Test
	void 空白や改行だけ_本文なしは400() throws Exception {
		long me = newUser("空白");
		as(me, post("/api/posts"), json(" \n　\t "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("本文を入力してください"));
		as(me, post("/api/posts"), "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("本文を入力してください"));
	}

	@Test
	void ログインしていないと401_CSRFトークンがないと403() throws Exception {
		mockMvc.perform(get("/api/posts")).andExpect(status().isUnauthorized());
		long me = newUser("CSRF");
		mockMvc.perform(post("/api/posts").cookie(accessCookie(me))
				.contentType(MediaType.APPLICATION_JSON).content(json("こんにちは")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	// ===== タイムライン（TL-01〜TL-03） =====

	@Test
	void すべてタブでは_自分の投稿もほかの人の投稿も新しい順に出る() throws Exception {
		long me = newUser("自分");
		long other = newUser("ほかの人");
		long older = createPost(other, "先の投稿");
		long newer = createPost(me, "後の投稿");
		as(me, get("/api/posts?tab=all"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.posts[0].id").value(newer))
				.andExpect(jsonPath("$.posts[0].mine").value(true))
				.andExpect(jsonPath("$.posts[1].id").value(older))
				.andExpect(jsonPath("$.posts[1].mine").value(false));
	}

	@Test
	void フォロー中タブは_自分とフォローした人の投稿だけ() throws Exception {
		long me = newUser("自分");
		long followed = newUser("フォローした人");
		long stranger = newUser("知らない人");
		follow(me, followed);
		long mine = createPost(me, "自分の投稿");
		long fromFollowed = createPost(followed, "フォローした人の投稿");
		createPost(stranger, "知らない人の投稿");

		assertThat(timelineIds(me, "following", "")).containsExactly(fromFollowed, mine);
	}

	@Test
	void 二十件ずつ読み_beforeで続きを読むと抜けも重なりもない() throws Exception {
		long me = newUser("たくさん投稿する人");
		// 25件。同じ時刻の投稿もまぜる（同じ時刻なら ID の大きい順）
		OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
		List<Long> expected = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			expected.add(0, insertPost(me, "投稿 " + i, base.plusMinutes(i / 3)));
		}

		String first = as(me, get("/api/posts?tab=following"), null)
				.andExpect(jsonPath("$.posts.length()").value(20))
				.andExpect(jsonPath("$.hasMore").value(true))
				.andReturn().getResponse().getContentAsString();
		List<Long> firstIds = ids(first);
		long last = firstIds.get(firstIds.size() - 1);

		String second = as(me, get("/api/posts?tab=following&before=" + last), null)
				.andExpect(jsonPath("$.posts.length()").value(5))
				.andExpect(jsonPath("$.hasMore").value(false))
				.andReturn().getResponse().getContentAsString();

		List<Long> all = new ArrayList<>(firstIds);
		all.addAll(ids(second));
		assertThat(all).isEqualTo(expected);
	}

	@Test
	void afterで新しい投稿だけを取れる() throws Exception {
		long me = newUser("自分");
		long followed = newUser("フォローした人");
		follow(me, followed);
		long top = createPost(followed, "画面で一番上の投稿");
		long newer1 = createPost(followed, "新しい投稿1");
		long newer2 = createPost(followed, "新しい投稿2");

		assertThat(timelineIds(me, "following", "&after=" + top)).containsExactly(newer2, newer1);
	}

	@Test
	void 新しい投稿の数は_自分の投稿を数えず_フォロー中タブはフォローした人だけ数える() throws Exception {
		long me = newUser("自分");
		long followed = newUser("フォローした人");
		long stranger = newUser("知らない人");
		follow(me, followed);
		long top = createPost(me, "画面で一番上の投稿");
		createPost(me, "自分の新しい投稿");
		createPost(followed, "フォローした人の新しい投稿");
		createPost(stranger, "知らない人の新しい投稿");

		as(me, get("/api/posts/new-count?tab=following&after=" + top), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(1));
		as(me, get("/api/posts/new-count?tab=all&after=" + top), null)
				.andExpect(jsonPath("$.count").value(2));
	}

	@Test
	void タブの値が違うと400() throws Exception {
		long me = newUser("自分");
		as(me, get("/api/posts?tab=popular"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("tab"));
	}

	// ===== 詳細・編集・削除（P-02〜P-04） =====

	@Test
	void 自分の投稿を編集すると_編集済みになり_並び順は変わらない() throws Exception {
		long me = newUser("自分");
		long first = createPost(me, "1つ目");
		long second = createPost(me, "2つ目");

		as(me, put("/api/posts/" + first), json("1つ目（直した）"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("1つ目（直した）"))
				.andExpect(jsonPath("$.editedAt").isNotEmpty());

		assertThat(timelineIds(me, "following", "")).containsExactly(second, first);
	}

	@Test
	void 他人の投稿は編集も削除もできず404で_DBは変わらない() throws Exception {
		long owner = newUser("投稿した人");
		long other = newUser("ほかの人");
		long postId = createPost(owner, "元の本文");

		as(other, put("/api/posts/" + postId), json("書きかえ"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("この投稿は見つかりません"));
		as(other, delete("/api/posts/" + postId), null).andExpect(status().isNotFound());

		String content = jdbcTemplate.queryForObject("SELECT content FROM posts WHERE id = ?", String.class, postId);
		assertThat(content).isEqualTo("元の本文");
	}

	@Test
	void 自分の投稿を削除すると204で_詳細は404になる() throws Exception {
		long me = newUser("自分");
		long postId = createPost(me, "消す投稿");
		as(me, get("/api/posts/" + postId), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("消す投稿"));

		as(me, delete("/api/posts/" + postId), null).andExpect(status().isNoContent());

		as(me, get("/api/posts/" + postId), null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
	}

	// ===== 道具 =====

	/** ユーザーを DB に作る（パスワードは使わないので仮の値） */
	private long newUser(String displayName) {
		String username = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO users (email, password_hash, username, display_name)
				VALUES (?, 'not-used', ?, ?) RETURNING id
				""", Long.class, username + "@example.com", username, displayName);
		assertThat(id).isNotNull();
		return id;
	}

	private void follow(long follower, long following) {
		jdbcTemplate.update("INSERT INTO follows (follower_id, following_id) VALUES (?, ?)", follower, following);
	}

	/** 投稿の日時を決めて、DB に直接入れる */
	private long insertPost(long userId, String content, OffsetDateTime createdAt) {
		Long id = jdbcTemplate.queryForObject(
				"INSERT INTO posts (user_id, content, created_at) VALUES (?, ?, ?) RETURNING id",
				Long.class, userId, content, createdAt);
		assertThat(id).isNotNull();
		return id;
	}

	/** API で投稿して、その ID を返す */
	private long createPost(long userId, String content) throws Exception {
		String body = as(userId, post("/api/posts"), json(content))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private List<Long> timelineIds(long viewer, String tab, String query) throws Exception {
		String body = as(viewer, get("/api/posts?tab=" + tab + query), null)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return ids(body);
	}

	private static List<Long> ids(String timelineJson) {
		List<Number> ids = JsonPath.read(timelineJson, "$.posts[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	/** {"content": "..."}。改行や " は JSON の書き方に直す */
	private static String json(String content) {
		return "{\"content\": \"" + JSONValue.escape(content) + "\"}";
	}

	private Cookie accessCookie(long userId) {
		return new Cookie(AuthCookies.ACCESS_TOKEN, jwtTokenProvider.createAccessToken(userId));
	}

	/** ログインした人として、画面と同じ形で CSRF トークンを付けて送る */
	private ResultActions as(long userId, MockHttpServletRequestBuilder builder, String json) throws Exception {
		Cookie csrf = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).as("Cookie XSRF-TOKEN").isNotNull();
		builder.cookie(csrf, accessCookie(userId)).header("X-XSRF-TOKEN", csrf.getValue());
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(builder);
	}

}
