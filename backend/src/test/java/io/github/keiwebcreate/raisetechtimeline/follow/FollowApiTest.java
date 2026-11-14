package io.github.keiwebcreate.raisetechtimeline.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;
import io.github.keiwebcreate.raisetechtimeline.auth.AuthCookies;
import io.github.keiwebcreate.raisetechtimeline.auth.JwtTokenProvider;
import io.github.keiwebcreate.raisetechtimeline.support.SqlCounter;
import jakarta.servlet.http.Cookie;

/**
 * フォローの API のテスト（docs/features/profile.md 4章）。本物の PostgreSQL 18（Testcontainers）を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FollowApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SqlCounter sqlCounter;

	// ===== フォロー・解除（FL-01） =====

	@Test
	void フォローするとフォロワー数が増え_解除すると減る() throws Exception {
		long me = newUser();
		long other = newUser();
		follow(newUser(), other);

		as(me, put(followUrl(other)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.following").value(true))
				.andExpect(jsonPath("$.followerCount").value(2));
		assertThat(isFollowing(me, other)).isTrue();

		as(me, delete(followUrl(other)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.following").value(false))
				.andExpect(jsonPath("$.followerCount").value(1));
		assertThat(isFollowing(me, other)).isFalse();
	}

	@Test
	void 二回フォローしても1つのまま_解除は何回でもエラーにならない() throws Exception {
		long me = newUser();
		long other = newUser();
		as(me, put(followUrl(other))).andExpect(status().isOk());
		as(me, put(followUrl(other))).andExpect(status().isOk()).andExpect(jsonPath("$.followerCount").value(1));
		as(me, delete(followUrl(other))).andExpect(status().isOk());
		as(me, delete(followUrl(other))).andExpect(status().isOk()).andExpect(jsonPath("$.followerCount").value(0));
	}

	@Test
	void 同時に何回フォローしても_500にならず1つだけ() throws Exception {
		long me = newUser();
		long other = newUser();
		Cookie csrf = csrfCookie();

		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				tasks.add(() -> mockMvc.perform(withAuth(me, put(followUrl(other)), csrf))
						.andReturn().getResponse().getStatus());
			}
			for (Future<Integer> result : pool.invokeAll(tasks)) {
				assertThat(result.get()).isEqualTo(200);
			}
		} finally {
			pool.shutdown();
		}
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM follows WHERE follower_id = ? AND following_id = ?", Long.class, me, other))
				.isEqualTo(1);
	}

	@Test
	void 自分自身はフォローできない() throws Exception {
		long me = newUser();
		as(me, put(followUrl(me)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"))
				.andExpect(jsonPath("$.detail").value("自分自身はフォローできません"));
		assertThat(isFollowing(me, me)).isFalse();
	}

	@Test
	void ユーザー名は大文字でもよく_ない人は404() throws Exception {
		long me = newUser();
		long other = newUser();
		as(me, put("/api/users/" + usernameOf(other).toUpperCase() + "/follow")).andExpect(status().isOk());
		assertThat(isFollowing(me, other)).isTrue();

		as(me, put("/api/users/nobody_here_x/follow"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
		as(me, delete("/api/users/nobody_here_x/follow")).andExpect(status().isNotFound());
		as(me, get("/api/users/nobody_here_x/following")).andExpect(status().isNotFound());
		as(me, get("/api/users/nobody_here_x/followers")).andExpect(status().isNotFound());
	}

	@Test
	void ログインしていないとフォローも一覧もできない() throws Exception {
		long other = newUser();
		mockMvc.perform(put(followUrl(other))).andExpect(status().is4xxClientError());
		mockMvc.perform(get("/api/users/" + usernameOf(other) + "/followers"))
				.andExpect(status().is4xxClientError());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM follows WHERE following_id = ?", Long.class,
				other)).isZero();
	}

	@Test
	void フォローした人の投稿がフォロー中タブに出て_解除すると消える() throws Exception {
		long me = newUser();
		long other = newUser();
		long postId = insertPost(other);

		as(me, put(followUrl(other))).andExpect(status().isOk());
		as(me, get("/api/posts?tab=following")).andExpect(jsonPath("$.posts[0].id").value(postId));

		as(me, delete(followUrl(other))).andExpect(status().isOk());
		as(me, get("/api/posts?tab=following")).andExpect(jsonPath("$.posts.length()").value(0));
	}

	// ===== フォローの状態（FL-02・4.3） =====

	@Test
	void プロフィールに_自分がフォローしているかと_相手にフォローされているかが出る() throws Exception {
		long me = newUser();
		long other = newUser();
		as(me, get(profileUrl(other)))
				.andExpect(jsonPath("$.following").value(false))
				.andExpect(jsonPath("$.followedBy").value(false));

		// 相手だけがフォローしている → 画面は「フォローされています」「フォローバック」
		follow(other, me);
		as(me, get(profileUrl(other)))
				.andExpect(jsonPath("$.following").value(false))
				.andExpect(jsonPath("$.followedBy").value(true));

		follow(me, other);
		as(me, get(profileUrl(other)))
				.andExpect(jsonPath("$.following").value(true))
				.andExpect(jsonPath("$.followedBy").value(true))
				.andExpect(jsonPath("$.followerCount").value(1));
	}

	@Test
	void いいねした人とコメントを書いた人にも_フォローの状態と自己紹介が出る() throws Exception {
		long me = newUser();
		long liker = newUser();
		jdbcTemplate.update("UPDATE users SET bio = ? WHERE id = ?", "デザインが好き", liker);
		long postId = insertPost(me);
		jdbcTemplate.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", postId, liker);
		Long commentId = jdbcTemplate.queryForObject(
				"INSERT INTO comments (post_id, user_id, content) VALUES (?, ?, 'がんばって') RETURNING id", Long.class,
				postId, liker);
		jdbcTemplate.update("INSERT INTO comment_likes (comment_id, user_id) VALUES (?, ?)", commentId, liker);
		follow(liker, me);

		as(me, get("/api/posts/" + postId + "/likes"))
				.andExpect(jsonPath("$.users[0].bio").value("デザインが好き"))
				.andExpect(jsonPath("$.users[0].following").value(false))
				.andExpect(jsonPath("$.users[0].followedBy").value(true));
		as(me, get("/api/comments/" + commentId + "/likes"))
				.andExpect(jsonPath("$.users[0].followedBy").value(true));
		as(me, get("/api/posts/" + postId + "/comments"))
				.andExpect(jsonPath("$.comments[0].author.following").value(false))
				.andExpect(jsonPath("$.comments[0].author.followedBy").value(true));

		follow(me, liker);
		as(me, get("/api/posts/" + postId + "/likes")).andExpect(jsonPath("$.users[0].following").value(true));
		as(me, get("/api/posts/" + postId + "/comments"))
				.andExpect(jsonPath("$.comments[0].author.following").value(true));
	}

	// ===== フォロー中・フォロワーの一覧（FL-04） =====

	@Test
	void フォロー中の一覧は新しくフォローした順に20件ずつ_続きはnextBeforeで読む() throws Exception {
		long me = newUser();
		long target = newUser();
		long first = newUser();
		follow(target, first);
		for (int i = 0; i < 20; i++) {
			follow(target, newUser());
		}

		String page1 = as(me, get("/api/users/" + usernameOf(target) + "/following"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users.length()").value(20))
				.andExpect(jsonPath("$.nextBefore").isNumber())
				.andReturn().getResponse().getContentAsString();
		Number nextBefore = JsonPath.read(page1, "$.nextBefore");

		as(me, get("/api/users/" + usernameOf(target) + "/following?before=" + nextBefore))
				.andExpect(jsonPath("$.users.length()").value(1))
				.andExpect(jsonPath("$.users[0].id").value(first))
				.andExpect(jsonPath("$.nextBefore").isEmpty());
	}

	@Test
	void フォロワーの一覧には_見ている人から見たフォローの状態と自己紹介が出て_メールアドレスは出ない() throws Exception {
		long me = newUser();
		long target = newUser();
		long older = newUser();
		long newer = newUser();
		jdbcTemplate.update("UPDATE users SET bio = ? WHERE id = ?", "よろしく", newer);
		follow(older, target);
		follow(newer, target);
		follow(me, newer);
		follow(older, me);

		as(me, get("/api/users/" + usernameOf(target) + "/followers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users.length()").value(2))
				.andExpect(jsonPath("$.users[0].id").value(newer))
				.andExpect(jsonPath("$.users[0].bio").value("よろしく"))
				.andExpect(jsonPath("$.users[0].iconUrl").isEmpty())
				.andExpect(jsonPath("$.users[0].following").value(true))
				.andExpect(jsonPath("$.users[0].followedBy").value(false))
				.andExpect(jsonPath("$.users[0].email").doesNotExist())
				.andExpect(jsonPath("$.users[1].id").value(older))
				.andExpect(jsonPath("$.users[1].following").value(false))
				.andExpect(jsonPath("$.users[1].followedBy").value(true))
				.andExpect(jsonPath("$.nextBefore").isEmpty());
	}

	@Test
	void 一覧が空なら空の配列() throws Exception {
		long me = newUser();
		as(me, get("/api/users/" + usernameOf(me) + "/following"))
				.andExpect(jsonPath("$.users.length()").value(0))
				.andExpect(jsonPath("$.nextBefore").isEmpty());
		as(me, get("/api/users/" + usernameOf(me) + "/followers")).andExpect(jsonPath("$.users.length()").value(0));
	}

	// ===== N+1 問題を起こさないこと =====

	@Test
	void 一覧のSQLの数は_1人でも20人でも同じ() throws Exception {
		long me = newUser();
		long target = newUser();
		addFollowerWithIcon(target, me);
		String followers = "/api/users/" + usernameOf(target) + "/followers";
		int one = sqlCountOf(me, followers);
		for (int i = 0; i < 19; i++) {
			addFollowerWithIcon(target, me);
		}
		int twenty = sqlCountOf(me, followers);

		// ユーザーを探す1回、一覧（フォローの状態も）1回
		assertThat(one).as("1人のときの SQL の数").isBetween(1, 3);
		assertThat(twenty).as("20人のときの SQL の数（1人のときは %d）", one).isEqualTo(one);
	}

	@Test
	void いいねした人とコメントのSQLの数も_フォローの状態を足しても1人でも20人でも同じ() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		addLikerAndComment(postId, me);
		int likersOne = sqlCountOf(me, "/api/posts/" + postId + "/likes");
		int commentsOne = sqlCountOf(me, "/api/posts/" + postId + "/comments");
		for (int i = 0; i < 19; i++) {
			addLikerAndComment(postId, me);
		}
		assertThat(sqlCountOf(me, "/api/posts/" + postId + "/likes")).isEqualTo(likersOne);
		assertThat(sqlCountOf(me, "/api/posts/" + postId + "/comments")).isEqualTo(commentsOne);
	}

	// ===== 道具 =====

	private long newUser() {
		String username = "f_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO users (email, password_hash, username, display_name)
				VALUES (?, 'not-used', ?, ?) RETURNING id
				""", Long.class, username + "@example.com", username, "フォロー 太郎");
		assertThat(id).isNotNull();
		return id;
	}

	private String usernameOf(long userId) {
		return jdbcTemplate.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
	}

	private String followUrl(long userId) {
		return "/api/users/" + usernameOf(userId) + "/follow";
	}

	private String profileUrl(long userId) {
		return "/api/users/" + usernameOf(userId);
	}

	private long insertPost(long userId) {
		Long id = jdbcTemplate.queryForObject(
				"INSERT INTO posts (user_id, content) VALUES (?, 'フォローのテスト') RETURNING id", Long.class, userId);
		assertThat(id).isNotNull();
		return id;
	}

	private void follow(long follower, long following) {
		jdbcTemplate.update("INSERT INTO follows (follower_id, following_id) VALUES (?, ?)", follower, following);
	}

	private boolean isFollowing(long follower, long following) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM follows WHERE follower_id = ? AND following_id = ?)", Boolean.class,
				follower, following));
	}

	// アイコン付きの人を足して target をフォローさせ、半分は viewer もフォローする（状態が混ざるように）
	private void addFollowerWithIcon(long target, long viewer) {
		long user = newUser();
		jdbcTemplate.update("UPDATE users SET icon_image_key = ? WHERE id = ?", "icons/" + UUID.randomUUID() + ".webp",
				user);
		follow(user, target);
		if (user % 2 == 0) {
			follow(viewer, user);
		}
	}

	private void addLikerAndComment(long postId, long viewer) {
		long user = newUser();
		jdbcTemplate.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", postId, user);
		jdbcTemplate.update("INSERT INTO comments (post_id, user_id, content) VALUES (?, ?, 'コメント')",
				postId, user);
		follow(user, viewer);
	}

	// GET を1回送り、そのあいだに MyBatis が送った SQL の数を返す
	private int sqlCountOf(long userId, String url) throws Exception {
		sqlCounter.reset();
		mockMvc.perform(get(url).cookie(accessCookie(userId))).andExpect(status().isOk());
		return sqlCounter.count();
	}

	private Cookie accessCookie(long userId) {
		return new Cookie(AuthCookies.ACCESS_TOKEN, jwtTokenProvider.createAccessToken(userId));
	}

	private Cookie csrfCookie() throws Exception {
		Cookie csrf = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).as("Cookie XSRF-TOKEN").isNotNull();
		return csrf;
	}

	private MockHttpServletRequestBuilder withAuth(long userId, MockHttpServletRequestBuilder builder, Cookie csrf) {
		return builder.cookie(csrf, accessCookie(userId)).header("X-XSRF-TOKEN", csrf.getValue());
	}

	private ResultActions as(long userId, MockHttpServletRequestBuilder builder) throws Exception {
		return mockMvc.perform(withAuth(userId, builder, csrfCookie()));
	}

}
