package io.github.keiwebcreate.raisetechtimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import io.github.keiwebcreate.raisetechtimeline.support.SqlCounter;
import jakarta.servlet.http.Cookie;

/**
 * コメント・返信と、コメントへのいいねの API のテスト（docs/features/comment.md・like.md 4.4）。
 * 本物の PostgreSQL 18（Testcontainers）を使う。あわせて、一覧で N+1 問題が起きていないかを SQL の数で確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommentApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SqlCounter sqlCounter;

	// ===== コメントする・一覧（C-01・C-02・C-03） =====

	@Test
	void 他人の投稿にコメントすると_一覧に出て_投稿のコメント数が増える() throws Exception {
		long author = newUser();
		long me = newUser();
		long postId = insertPost(author);

		as(me, post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"  がんばって！\\n  \"}"))
				.andExpect(status().isCreated())
				// 前後の空白・改行は取り除く
				.andExpect(jsonPath("$.content").value("がんばって！"))
				.andExpect(jsonPath("$.postId").value(postId))
				.andExpect(jsonPath("$.parentCommentId").isEmpty())
				.andExpect(jsonPath("$.rootCommentId").isEmpty())
				.andExpect(jsonPath("$.replyTo").isEmpty())
				.andExpect(jsonPath("$.author.id").value(me))
				.andExpect(jsonPath("$.author.email").doesNotExist())
				.andExpect(jsonPath("$.mine").value(true))
				.andExpect(jsonPath("$.likeCount").value(0))
				.andExpect(jsonPath("$.replyCount").value(0));

		as(author, get("/api/posts/" + postId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments.length()").value(1))
				.andExpect(jsonPath("$.comments[0].mine").value(false))
				.andExpect(jsonPath("$.nextAfter").isEmpty());
		as(author, get("/api/posts/" + postId)).andExpect(jsonPath("$.commentCount").value(1));
	}

	@Test
	void 自分の投稿にもコメントできる() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		comment(me, postId, "自分で補足").andExpect(status().isCreated());
	}

	@Test
	void 本文は1から280文字_絵文字も1文字() throws Exception {
		long me = newUser();
		long postId = insertPost(me);

		comment(me, postId, " \n ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("content"))
				.andExpect(jsonPath("$.errors[0].message").value("コメントを入力してください"));
		as(me, post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("コメントを入力してください"));
		// 絵文字（Java の char では2つ）を280個はちょうど上限
		comment(me, postId, "😀".repeat(280)).andExpect(status().isCreated());
		comment(me, postId, "あ".repeat(281))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("280文字以内で入力してください"));
	}

	@Test
	void ない投稿は404() throws Exception {
		long me = newUser();
		comment(me, 999999999L, "こんにちは")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("この投稿は見つかりません"));
		as(me, get("/api/posts/999999999/comments")).andExpect(status().isNotFound());
	}

	@Test
	void コメントは古い順に50件ずつ_続きはnextAfterで読む() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 51; i++) {
			ids.add(insertComment(postId, me, "コメント" + i));
		}

		String first = as(me, get("/api/posts/" + postId + "/comments"))
				.andExpect(jsonPath("$.comments.length()").value(50))
				// 一番古いコメントが先頭
				.andExpect(jsonPath("$.comments[0].id").value(ids.getFirst()))
				.andReturn().getResponse().getContentAsString();
		Number nextAfter = JsonPath.read(first, "$.nextAfter");
		assertThat(nextAfter).isNotNull();

		as(me, get("/api/posts/" + postId + "/comments?after=" + nextAfter))
				.andExpect(jsonPath("$.comments.length()").value(1))
				.andExpect(jsonPath("$.comments[0].id").value(ids.getLast()))
				.andExpect(jsonPath("$.nextAfter").isEmpty());
	}

	// ===== 返信（C-05） =====

	@Test
	void 返信と返信の返信は_一番上のコメントの下に1段で並び_返信先が分かる() throws Exception {
		long hanako = newUser();
		long me = newUser();
		long postId = insertPost(me);
		long top = idOf(comment(hanako, postId, "がんばって！"));

		long reply = idOf(reply(me, postId, top, "ありがとう")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentCommentId").value(top))
				.andExpect(jsonPath("$.rootCommentId").value(top))
				.andExpect(jsonPath("$.replyTo.username").value(usernameOf(hanako))));
		reply(hanako, postId, reply, "どういたしまして！")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentCommentId").value(reply))
				// 返信の返信も、一番上のコメントは同じ
				.andExpect(jsonPath("$.rootCommentId").value(top))
				.andExpect(jsonPath("$.replyTo.username").value(usernameOf(me)));

		// 投稿への直接のコメントの一覧には、返信は出ない。返信の数は「直接」と「並ぶものすべて」の2つ
		as(me, get("/api/posts/" + postId + "/comments"))
				.andExpect(jsonPath("$.comments.length()").value(1))
				.andExpect(jsonPath("$.comments[0].replyCount").value(1))
				.andExpect(jsonPath("$.comments[0].threadReplyCount").value(2));
		// 投稿のコメント数は、直接のコメントだけ
		as(me, get("/api/posts/" + postId)).andExpect(jsonPath("$.commentCount").value(1));

		as(me, get("/api/comments/" + top + "/replies"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.replies.length()").value(2))
				.andExpect(jsonPath("$.replies[0].content").value("ありがとう"))
				.andExpect(jsonPath("$.replies[0].replyCount").value(1))
				.andExpect(jsonPath("$.replies[0].mine").value(true))
				.andExpect(jsonPath("$.replies[1].content").value("どういたしまして！"))
				.andExpect(jsonPath("$.replies[1].replyCount").value(0));
	}

	@Test
	void 別の投稿のコメントやないコメントには返信できない() throws Exception {
		long me = newUser();
		long postA = insertPost(me);
		long postB = insertPost(me);
		long commentOnB = insertComment(postB, me, "B へのコメント");

		reply(me, postA, commentOnB, "返信")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("返信先のコメントは削除されました"));
		reply(me, postA, 999999999L, "返信").andExpect(status().isNotFound());
		assertThat(countComments(postA)).isZero();
	}

	@Test
	void ないコメントの返信は404() throws Exception {
		as(newUser(), get("/api/comments/999999999/replies"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
	}

	// ===== 削除（C-04） =====

	@Test
	void 消すと下の返信といいねも一緒に消える() throws Exception {
		long me = newUser();
		long other = newUser();
		long postId = insertPost(other);
		long top = idOf(comment(me, postId, "消すコメント"));
		long reply = idOf(reply(other, postId, top, "返信"));
		idOf(reply(me, postId, reply, "返信の返信"));
		as(other, put("/api/comments/" + top + "/like")).andExpect(status().isOk());

		as(me, delete("/api/comments/" + top)).andExpect(status().isNoContent());

		assertThat(countComments(postId)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM comment_likes WHERE comment_id = ?", Long.class,
				top)).isZero();
		as(me, get("/api/posts/" + postId)).andExpect(jsonPath("$.commentCount").value(0));
	}

	@Test
	void 返信を消すと_その下だけ消えて上のコメントは残る() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		long top = idOf(comment(me, postId, "上"));
		long reply = idOf(reply(me, postId, top, "返信"));
		idOf(reply(me, postId, reply, "返信の返信"));
		idOf(reply(me, postId, top, "もう1つの返信"));

		as(me, delete("/api/comments/" + reply)).andExpect(status().isNoContent());

		as(me, get("/api/comments/" + top + "/replies"))
				.andExpect(jsonPath("$.replies.length()").value(1))
				.andExpect(jsonPath("$.replies[0].content").value("もう1つの返信"));
	}

	@Test
	void 他人のコメントは消せず404() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		// 投稿の持ち主でも、他人が付けたコメントは消せない（comment.md 7章）
		long others = insertComment(postId, newUser(), "他人のコメント");

		as(me, delete("/api/comments/" + others))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
		assertThat(countComments(postId)).isEqualTo(1);
		as(me, delete("/api/comments/999999999")).andExpect(status().isNotFound());
	}

	@Test
	void 投稿を消すとコメントも消える() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		long top = insertComment(postId, newUser(), "コメント");
		reply(me, postId, top, "返信").andExpect(status().isCreated());

		as(me, delete("/api/posts/" + postId)).andExpect(status().isNoContent());
		assertThat(countComments(postId)).isZero();
	}

	@Test
	void ログインしていないとコメントできず_IDが数字でなければ404() throws Exception {
		long postId = insertPost(newUser());
		mockMvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(status().is4xxClientError());
		as(newUser(), get("/api/comments/abc/replies")).andExpect(status().isNotFound());
	}

	// ===== コメントへのいいね（L-04） =====

	@Test
	void コメントにいいねすると_一覧の数と自分がいいねしたかに出る() throws Exception {
		long me = newUser();
		long other = newUser();
		long postId = insertPost(other);
		long commentId = insertComment(postId, other, "いいねされるコメント");

		as(me, put("/api/comments/" + commentId + "/like"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.likeCount").value(1));
		// 2回押しても1つのまま
		as(me, put("/api/comments/" + commentId + "/like")).andExpect(jsonPath("$.likeCount").value(1));

		as(me, get("/api/posts/" + postId + "/comments"))
				.andExpect(jsonPath("$.comments[0].likeCount").value(1))
				.andExpect(jsonPath("$.comments[0].liked").value(true));
		as(other, get("/api/posts/" + postId + "/comments"))
				.andExpect(jsonPath("$.comments[0].liked").value(false));

		as(other, get("/api/comments/" + commentId + "/likes"))
				.andExpect(jsonPath("$.users.length()").value(1))
				.andExpect(jsonPath("$.users[0].id").value(me))
				.andExpect(jsonPath("$.nextBefore").isEmpty());

		as(me, delete("/api/comments/" + commentId + "/like"))
				.andExpect(jsonPath("$.liked").value(false))
				.andExpect(jsonPath("$.likeCount").value(0));
		as(me, delete("/api/comments/" + commentId + "/like")).andExpect(status().isOk());
	}

	@Test
	void ないコメントへのいいねは404() throws Exception {
		long me = newUser();
		as(me, put("/api/comments/999999999/like"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
		as(me, delete("/api/comments/999999999/like")).andExpect(status().isNotFound());
		as(me, get("/api/comments/999999999/likes")).andExpect(status().isNotFound());
	}

	// ===== N+1 問題がないこと =====

	@Test
	void コメントの一覧のSQLの数は_1件でも50件でも同じ() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		addCommentWithReplyAndLike(postId, me);
		int one = sqlCountOf(me, "/api/posts/" + postId + "/comments");

		for (int i = 0; i < 49; i++) {
			addCommentWithReplyAndLike(postId, me);
		}
		int fifty = sqlCountOf(me, "/api/posts/" + postId + "/comments");

		// 投稿があるかを1回、コメントと数を1回
		assertThat(one).as("1件のときの SQL の数").isBetween(1, 3);
		assertThat(fifty).as("50件のときの SQL の数（1件のときは %d）", one).isEqualTo(one);
		as(me, get("/api/posts/" + postId + "/comments"))
				.andExpect(jsonPath("$.comments.length()").value(50))
				.andExpect(jsonPath("$.comments[0].likeCount").value(1))
				.andExpect(jsonPath("$.comments[0].liked").value(true))
				.andExpect(jsonPath("$.comments[0].replyCount").value(1));
	}

	@Test
	void 返信の一覧のSQLの数は_1件でも20件でも同じ() throws Exception {
		long me = newUser();
		long postId = insertPost(me);
		long top = insertComment(postId, me, "上");
		long parent = insertReply(postId, me, top, top, "返信0");
		int one = sqlCountOf(me, "/api/comments/" + top + "/replies");

		for (int i = 1; i < 20; i++) {
			// 返信の返信を続ける（返信先がばらばらでも、1回で読めること）
			parent = insertReply(postId, newUser(), parent, top, "返信" + i);
		}
		int twenty = sqlCountOf(me, "/api/comments/" + top + "/replies");

		// コメントがあるかを1回、返信と数を1回
		assertThat(one).as("1件のときの SQL の数").isBetween(1, 3);
		assertThat(twenty).as("20件のときの SQL の数（1件のときは %d）", one).isEqualTo(one);
	}

	// ===== 道具 =====

	private long newUser() {
		String username = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO users (email, password_hash, username, display_name)
				VALUES (?, 'not-used', ?, ?) RETURNING id
				""", Long.class, username + "@example.com", username, "コメント 太郎");
		assertThat(id).isNotNull();
		return id;
	}

	private String usernameOf(long userId) {
		return jdbcTemplate.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
	}

	private long insertPost(long userId) {
		Long id = jdbcTemplate.queryForObject(
				"INSERT INTO posts (user_id, content) VALUES (?, 'コメントのテスト') RETURNING id", Long.class, userId);
		assertThat(id).isNotNull();
		return id;
	}

	private long insertComment(long postId, long userId, String content) {
		Long id = jdbcTemplate.queryForObject(
				"INSERT INTO comments (post_id, user_id, content) VALUES (?, ?, ?) RETURNING id", Long.class, postId,
				userId, content);
		assertThat(id).isNotNull();
		return id;
	}

	private long insertReply(long postId, long userId, long parentId, long rootId, String content) {
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO comments (post_id, user_id, parent_comment_id, root_comment_id, content)
				VALUES (?, ?, ?, ?, ?) RETURNING id
				""", Long.class, postId, userId, parentId, rootId, content);
		assertThat(id).isNotNull();
		return id;
	}

	// コメントに、返信1件と、見ている人（liker）のいいねを付ける
	private void addCommentWithReplyAndLike(long postId, long liker) {
		long commentId = insertComment(postId, newUser(), "コメント");
		insertReply(postId, newUser(), commentId, commentId, "返信");
		jdbcTemplate.update("INSERT INTO comment_likes (comment_id, user_id) VALUES (?, ?)", commentId, liker);
	}

	private long countComments(long postId) {
		Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM comments WHERE post_id = ?", Long.class, postId);
		assertThat(count).isNotNull();
		return count;
	}

	private ResultActions comment(long userId, long postId, String content) throws Exception {
		return as(userId, post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
				.content(json(content, null)));
	}

	private ResultActions reply(long userId, long postId, long parentId, String content) throws Exception {
		return as(userId, post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
				.content(json(content, parentId)));
	}

	private static String json(String content, Long parentId) {
		String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		return parentId == null ? "{\"content\":\"" + escaped + "\"}"
				: "{\"content\":\"" + escaped + "\",\"parentCommentId\":" + parentId + "}";
	}

	private static long idOf(ResultActions result) throws Exception {
		Number id = JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.id");
		return id.longValue();
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

	private ResultActions as(long userId, MockHttpServletRequestBuilder builder) throws Exception {
		Cookie csrf = csrfCookie();
		return mockMvc.perform(builder.cookie(csrf, accessCookie(userId)).header("X-XSRF-TOKEN", csrf.getValue()));
	}

}
