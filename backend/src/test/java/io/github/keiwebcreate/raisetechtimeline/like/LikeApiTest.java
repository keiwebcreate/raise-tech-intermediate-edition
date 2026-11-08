package io.github.keiwebcreate.raisetechtimeline.like;

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
 * 投稿へのいいねの API のテスト（docs/features/like.md）。本物の PostgreSQL 18（Testcontainers）を使う。
 * あわせて、タイムラインで N+1 問題（投稿の件数だけ SQL が増える）が起きていないかを、SQL の数で確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LikeApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SqlCounter sqlCounter;

	// ===== いいね・取り消し（L-01） =====

	@Test
	void 他人の投稿にいいねすると_数が増え_投稿の返事にも出る() throws Exception {
		long author = newUser();
		long me = newUser();
		long postId = insertPost(author);

		as(me, put("/api/posts/" + postId + "/like"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));

		as(me, get("/api/posts/" + postId))
				.andExpect(jsonPath("$.likeCount").value(1))
				.andExpect(jsonPath("$.liked").value(true));
		// 投稿した人から見ると、数は1で、自分はいいねしていない
		as(author, get("/api/posts/" + postId))
				.andExpect(jsonPath("$.likeCount").value(1))
				.andExpect(jsonPath("$.liked").value(false));
	}

	@Test
	void 自分の投稿にもいいねできる() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		as(me, put("/api/posts/" + postId + "/like")).andExpect(jsonPath("$.likeCount").value(1));
	}

	@Test
	void 二回いいねしても1つのまま_取り消しは何回でもエラーにならない() throws Exception {
		long me = newUser();
		long postId = insertPost(newUser());

		as(me, put("/api/posts/" + postId + "/like")).andExpect(jsonPath("$.likeCount").value(1));
		as(me, put("/api/posts/" + postId + "/like"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.likeCount").value(1));

		as(me, delete("/api/posts/" + postId + "/like"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false))
				.andExpect(jsonPath("$.likeCount").value(0));
		as(me, delete("/api/posts/" + postId + "/like")).andExpect(status().isOk());
	}

	@Test
	void 同時に何回いいねしても_500にならず1つだけ() throws Exception {
		long me = newUser();
		long postId = insertPost(newUser());
		Cookie csrf = csrfCookie();

		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				tasks.add(() -> mockMvc.perform(withAuth(me, put("/api/posts/" + postId + "/like"), csrf))
						.andReturn().getResponse().getStatus());
			}
			for (Future<Integer> result : pool.invokeAll(tasks)) {
				assertThat(result.get()).isEqualTo(200);
			}
		} finally {
			pool.shutdown();
		}
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM likes WHERE post_id = ?", Long.class, postId))
				.isEqualTo(1);
	}

	@Test
	void ない投稿は404() throws Exception {
		long me = newUser();
		as(me, put("/api/posts/999999999/like"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
		as(me, delete("/api/posts/999999999/like")).andExpect(status().isNotFound());
		as(me, get("/api/posts/999999999/likes")).andExpect(status().isNotFound());
	}

	@Test
	void 投稿を消すといいねも消える() throws Exception {
		long author = newUser();
		long postId = insertPost(author);
		as(newUser(), put("/api/posts/" + postId + "/like"));

		as(author, delete("/api/posts/" + postId)).andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM likes WHERE post_id = ?", Long.class, postId))
				.isZero();
	}

	@Test
	void ログインしていないといいねできない() throws Exception {
		long postId = insertPost(newUser());
		mockMvc.perform(put("/api/posts/" + postId + "/like")).andExpect(status().is4xxClientError());
	}

	// ===== いいねした人の一覧（L-03） =====

	@Test
	void いいねした人は新しい順に20件ずつ_続きはnextBeforeで読む() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		List<Long> likers = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			long user = newUser();
			likers.add(user);
			as(user, put("/api/posts/" + postId + "/like"));
		}

		String first = as(me, get("/api/posts/" + postId + "/likes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users.length()").value(20))
				// 一番新しくいいねした人が先頭
				.andExpect(jsonPath("$.users[0].id").value(likers.getLast()))
				.andExpect(jsonPath("$.users[0].email").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		Number nextBefore = JsonPath.read(first, "$.nextBefore");
		assertThat(nextBefore).isNotNull();

		as(me, get("/api/posts/" + postId + "/likes?before=" + nextBefore))
				.andExpect(jsonPath("$.users.length()").value(1))
				// 一番古くいいねした人が最後
				.andExpect(jsonPath("$.users[0].id").value(likers.getFirst()))
				.andExpect(jsonPath("$.nextBefore").isEmpty());
	}

	// ===== N+1 問題がないこと =====

	@Test
	void タイムラインのSQLの数は_投稿が1件でも20件でも同じ() throws Exception {
		long me = newUser();
		long other = newUser();
		follow(me, other);

		likeAndAttachImage(insertPost(other), me);
		int one = sqlCountOf(me, "/api/posts?tab=following");

		for (int i = 0; i < 19; i++) {
			likeAndAttachImage(insertPost(other), me);
		}
		int twenty = sqlCountOf(me, "/api/posts?tab=following");

		// 数える道具が動いていること（0 なら数えられていない）と、投稿・画像・いいねを合わせて少ない回数で読めていること
		// （投稿と数を1回、画像・動画を1回）
		assertThat(one).as("1件のときの SQL の数").isBetween(1, 3);
		assertThat(twenty).as("20件のときの SQL の数（1件のときは %d）", one).isEqualTo(one);
		as(me, get("/api/posts?tab=following"))
				.andExpect(jsonPath("$.posts.length()").value(20))
				.andExpect(jsonPath("$.posts[0].likeCount").value(1))
				.andExpect(jsonPath("$.posts[0].liked").value(true))
				.andExpect(jsonPath("$.posts[0].media.length()").value(1));
	}

	@Test
	void いいねした人の一覧のSQLの数は_1人でも20人でも同じ() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		as(newUser(), put("/api/posts/" + postId + "/like"));
		int one = sqlCountOf(me, "/api/posts/" + postId + "/likes");

		for (int i = 0; i < 19; i++) {
			as(newUser(), put("/api/posts/" + postId + "/like"));
		}
		int twenty = sqlCountOf(me, "/api/posts/" + postId + "/likes");

		// 投稿があるかを1回、いいねした人を1回
		assertThat(one).as("1人のときの SQL の数").isBetween(1, 3);
		assertThat(twenty).as("20人のときの SQL の数（1人のときは %d）", one).isEqualTo(one);
	}

	// ===== 道具 =====

	private long newUser() {
		String username = "l_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO users (email, password_hash, username, display_name)
				VALUES (?, 'not-used', ?, ?) RETURNING id
				""", Long.class, username + "@example.com", username, "いいね 太郎");
		assertThat(id).isNotNull();
		return id;
	}

	private long insertPost(long userId) {
		Long id = jdbcTemplate.queryForObject("INSERT INTO posts (user_id, content) VALUES (?, 'いいねのテスト') RETURNING id",
				Long.class, userId);
		assertThat(id).isNotNull();
		return id;
	}

	private void follow(long follower, long following) {
		jdbcTemplate.update("INSERT INTO follows (follower_id, following_id) VALUES (?, ?)", follower, following);
	}

	// いいねと画像を付ける（画像は DB の行だけ。表示の URL を作るだけなので、S3 にファイルはいらない）
	private void likeAndAttachImage(long postId, long liker) {
		jdbcTemplate.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", postId, liker);
		jdbcTemplate.update("INSERT INTO post_media (post_id, media_type, s3_key, position) VALUES (?, 'image', ?, 1)",
				postId, "posts/2026/11/" + UUID.randomUUID() + ".webp");
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
