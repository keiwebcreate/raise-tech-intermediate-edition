package io.github.keiwebcreate.raisetechtimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import io.github.keiwebcreate.raisetechtimeline.TestcontainersConfiguration;
import io.github.keiwebcreate.raisetechtimeline.auth.AuthCookies;
import io.github.keiwebcreate.raisetechtimeline.auth.JwtTokenProvider;
import io.github.keiwebcreate.raisetechtimeline.support.SqlCounter;
import jakarta.servlet.http.Cookie;
import net.minidev.json.JSONObject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * プロフィールの表示・編集とアイコンの API のテスト（docs/features/profile.md 3章）。
 * 本物の PostgreSQL 18 と S3Mock（どちらも Testcontainers）を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProfileApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private S3Client s3;

	@Autowired
	private SqlCounter sqlCounter;

	// ===== 表示（PF-01） =====

	@Test
	void プロフィールには_自己紹介と登録日と_フォロー数_フォロワー数が出て_メールアドレスは出ない() throws Exception {
		long me = newUser();
		long other = newUser();
		jdbcTemplate.update("UPDATE users SET bio = ? WHERE id = ?", "Java を勉強中\nよろしく", other);
		follow(me, other);
		follow(newUser(), other);
		follow(other, newUser());

		as(me, get("/api/users/" + usernameOf(other)), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(other))
				.andExpect(jsonPath("$.bio").value("Java を勉強中\nよろしく"))
				.andExpect(jsonPath("$.iconUrl").isEmpty())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.followingCount").value(1))
				.andExpect(jsonPath("$.followerCount").value(2))
				.andExpect(jsonPath("$.mine").value(false))
				.andExpect(jsonPath("$.email").doesNotExist());
		as(me, get("/api/users/" + usernameOf(me)), null).andExpect(jsonPath("$.mine").value(true));
	}

	@Test
	void ユーザー名は大文字でも探せて_ない人は404() throws Exception {
		long me = newUser();
		as(me, get("/api/users/" + usernameOf(me).toUpperCase()), null).andExpect(status().isOk());
		as(me, get("/api/users/nobody_here_x"), null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("このアカウントは存在しません"));
		as(me, get("/api/users/nobody_here_x/posts"), null).andExpect(status().isNotFound());
	}

	@Test
	void その人の投稿だけが新しい順に20件ずつ出る() throws Exception {
		long me = newUser();
		long other = newUser();
		insertPost(me);
		long oldest = insertPost(other);
		for (int i = 0; i < 20; i++) {
			insertPost(other);
		}

		String first = as(me, get("/api/users/" + usernameOf(other) + "/posts"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.posts.length()").value(20))
				.andExpect(jsonPath("$.hasMore").value(true))
				.andExpect(jsonPath("$.posts[0].author.id").value(other))
				.andReturn().getResponse().getContentAsString();
		Number last = JsonPath.read(first, "$.posts[19].id");

		as(me, get("/api/users/" + usernameOf(other) + "/posts?before=" + last), null)
				.andExpect(jsonPath("$.posts.length()").value(1))
				.andExpect(jsonPath("$.posts[0].id").value(oldest))
				.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void 投稿の一覧のSQLの数は_1件でも20件でも同じ() throws Exception {
		long me = newUser();
		long other = newUser();
		setIcon(other);
		insertPost(other);
		int one = sqlCountOf(me, "/api/users/" + usernameOf(other) + "/posts");
		for (int i = 0; i < 19; i++) {
			insertPost(other);
		}
		int twenty = sqlCountOf(me, "/api/users/" + usernameOf(other) + "/posts");

		// ユーザーを探す1回、投稿と数を1回、画像・動画を1回
		assertThat(one).as("1件のときの SQL の数").isBetween(1, 4);
		assertThat(twenty).as("20件のときの SQL の数（1件のときは %d）", one).isEqualTo(one);
	}

	// ===== 編集（PF-02） =====

	@Test
	void 表示名と自己紹介を変えられる_前後の空白は取り除き_空の自己紹介はなしになる() throws Exception {
		long me = newUser();
		as(me, put("/api/users/me"), body("  新しい名前 ", "  1行目\n2行目  ", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("新しい名前"))
				.andExpect(jsonPath("$.bio").value("1行目\n2行目"))
				.andExpect(jsonPath("$.mine").value(true));
		as(me, put("/api/users/me"), body("新しい名前", "   ", null)).andExpect(jsonPath("$.bio").isEmpty());
		// ログイン中の本人の情報（左のメニュー）も変わる
		as(me, get("/api/auth/me"), null).andExpect(jsonPath("$.displayName").value("新しい名前"));
	}

	@Test
	void 表示名は1から50文字_自己紹介は160文字まで_絵文字も1文字() throws Exception {
		long me = newUser();
		expectFieldError(as(me, put("/api/users/me"), body("  ", null, null)), "displayName", "表示名を入力してください");
		expectFieldError(as(me, put("/api/users/me"), "{}"), "displayName", "表示名を入力してください");
		expectFieldError(as(me, put("/api/users/me"), body("あ".repeat(51), null, null)), "displayName",
				"表示名は50文字以内で入力してください");
		expectFieldError(as(me, put("/api/users/me"), body("名前", "あ".repeat(161), null)), "bio",
				"自己紹介は160文字以内で入力してください");
		// 絵文字（Java の char では2つ）は1文字と数える
		as(me, put("/api/users/me"), body("😀".repeat(50), "😀".repeat(160), null)).andExpect(status().isOk());
	}

	// ===== アイコン（PF-02） =====

	@Test
	void アイコンは真ん中を正方形に切り取った400四方のWebPになり_保存すると正式な場所へ移る() throws Exception {
		long me = newUser();
		// 横長（左右が赤、真ん中が青）。真ん中を切り取るので、青だけが残る
		String tmpKey = uploadedIconKey(me, png(sides(1200, 600)));
		assertThat(tmpKey).matches("tmp/" + me + "/icons/[0-9a-f-]{36}\\.webp");
		BufferedImage icon = ImageIO.read(new ByteArrayInputStream(s3Object(tmpKey)));
		assertThat(icon.getWidth()).isEqualTo(400);
		assertThat(icon.getHeight()).isEqualTo(400);
		assertThat(new Color(icon.getRGB(5, 200)).getBlue()).isGreaterThan(200);

		String body = as(me, put("/api/users/me"), body("名前", null, tmpKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.iconUrl").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(body, "$.iconKey");
		assertThat(key).matches("icons/[0-9a-f-]{36}\\.webp");
		assertThat(exists(key)).isTrue();
		assertThat(exists(tmpKey)).as("一時置き場のものは消える").isFalse();
	}

	@Test
	void 小さい画像も400四方になる() throws Exception {
		long me = newUser();
		String tmpKey = uploadedIconKey(me, png(sides(100, 80)));
		BufferedImage icon = ImageIO.read(new ByteArrayInputStream(s3Object(tmpKey)));
		assertThat(icon.getWidth()).isEqualTo(400);
		assertThat(icon.getHeight()).isEqualTo(400);
	}

	@Test
	void アイコンはJPEG_PNG_WebPだけ_GIFや画像でないものは断る() throws Exception {
		long me = newUser();
		expectFieldError(uploadIcon(me, encode(sides(10, 10), "gif")), "file", "JPEG・PNG・WebP の画像を選んでください");
		expectFieldError(uploadIcon(me, "画像ではない".getBytes()), "file", "JPEG・PNG・WebP の画像を選んでください");
	}

	@Test
	void アイコンを残す_差し替える_外すと_前の画像は消える() throws Exception {
		long me = newUser();
		String first = savedIconKey(me, uploadedIconKey(me, png(sides(50, 50))));

		// 残す：今のキーを送り返すと、そのまま
		as(me, put("/api/users/me"), body("名前", null, first)).andExpect(jsonPath("$.iconKey").value(first));
		assertThat(exists(first)).isTrue();

		// 差し替える：前の画像は消える
		String second = savedIconKey(me, uploadedIconKey(me, png(sides(60, 60))));
		assertThat(second).isNotEqualTo(first);
		assertThat(exists(first)).isFalse();

		// 外す：初期のアイコンに戻り、画像は消える
		as(me, put("/api/users/me"), body("名前", null, null))
				.andExpect(jsonPath("$.iconKey").isEmpty())
				.andExpect(jsonPath("$.iconUrl").isEmpty());
		assertThat(exists(second)).isFalse();
	}

	@Test
	void 他人の一時置き場のアイコンや_投稿の画像のキーや_でたらめなキーは使えない() throws Exception {
		long me = newUser();
		long other = newUser();
		String othersKey = uploadedIconKey(other, png(sides(50, 50)));
		String postImageKey = JsonPath.read(as(me, multipart("/api/media/images")
				.file(new MockMultipartFile("file", "a.png", "image/png", png(sides(50, 50)))), null)
				.andReturn().getResponse().getContentAsString(), "$.key");

		for (String key : List.of(othersKey, postImageKey, "icons/../secret.webp",
				"tmp/" + me + "/icons/00000000-0000-0000-0000-000000000000.webp")) {
			expectFieldError(as(me, put("/api/users/me"), body("名前", null, key)), "iconKey",
					"画像が見つかりません。選び直してください");
		}
		assertThat(exists(othersKey)).as("他人のファイルは消さない").isTrue();
	}

	@Test
	void アイコンは投稿_コメント_いいねした人_ログイン中の本人の返事にも出る() throws Exception {
		long me = newUser();
		setIcon(me);
		long postId = insertPost(me);
		jdbcTemplate.update("INSERT INTO comments (post_id, user_id, content) VALUES (?, ?, 'コメント')", postId, me);
		jdbcTemplate.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", postId, me);

		as(me, get("/api/posts/" + postId), null).andExpect(jsonPath("$.author.iconUrl").isNotEmpty());
		as(me, get("/api/posts?tab=all"), null).andExpect(jsonPath("$.posts[0].author.iconUrl").isNotEmpty());
		as(me, get("/api/posts/" + postId + "/comments"), null)
				.andExpect(jsonPath("$.comments[0].author.iconUrl").isNotEmpty());
		as(me, get("/api/posts/" + postId + "/likes"), null).andExpect(jsonPath("$.users[0].iconUrl").isNotEmpty());
		as(me, get("/api/auth/me"), null).andExpect(jsonPath("$.iconUrl").isNotEmpty());
	}

	@Test
	void ログインしていないと見られない() throws Exception {
		long me = newUser();
		mockMvc.perform(get("/api/users/" + usernameOf(me))).andExpect(status().is4xxClientError());
	}

	// ===== 道具 =====

	private long newUser() {
		String username = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Long id = jdbcTemplate.queryForObject("""
				INSERT INTO users (email, password_hash, username, display_name)
				VALUES (?, 'not-used', ?, ?) RETURNING id
				""", Long.class, username + "@example.com", username, "プロフィール 太郎");
		assertThat(id).isNotNull();
		return id;
	}

	private String usernameOf(long userId) {
		return jdbcTemplate.queryForObject("SELECT username FROM users WHERE id = ?", String.class, userId);
	}

	private void follow(long follower, long following) {
		jdbcTemplate.update("INSERT INTO follows (follower_id, following_id) VALUES (?, ?)", follower, following);
	}

	private long insertPost(long userId) {
		Long id = jdbcTemplate.queryForObject(
				"INSERT INTO posts (user_id, content) VALUES (?, 'プロフィールのテスト') RETURNING id", Long.class, userId);
		assertThat(id).isNotNull();
		return id;
	}

	// アイコンを付ける（DB の行だけ。表示の URL を作るだけなので、S3 にファイルはいらない）
	private void setIcon(long userId) {
		jdbcTemplate.update("UPDATE users SET icon_image_key = ? WHERE id = ?", "icons/" + UUID.randomUUID() + ".webp",
				userId);
	}

	private ResultActions uploadIcon(long userId, byte[] data) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", data);
		return as(userId, multipart("/api/media/icons").file(file), null);
	}

	private String uploadedIconKey(long userId, byte[] data) throws Exception {
		String body = uploadIcon(userId, data).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$.key");
	}

	private String savedIconKey(long userId, String tmpKey) throws Exception {
		String body = as(userId, put("/api/users/me"), body("名前", null, tmpKey))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.iconKey");
	}

	private static String body(String displayName, String bio, String iconKey) {
		JSONObject json = new JSONObject();
		json.put("displayName", displayName);
		json.put("bio", bio);
		json.put("iconKey", iconKey);
		return json.toJSONString();
	}

	private static void expectFieldError(ResultActions result, String field, String message) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value(field))
				.andExpect(jsonPath("$.errors[0].message").value(message));
	}

	private int sqlCountOf(long userId, String url) throws Exception {
		sqlCounter.reset();
		as(userId, get(url), null).andExpect(status().isOk());
		return sqlCounter.count();
	}

	private ResultActions as(long userId, AbstractMockHttpServletRequestBuilder<?> builder, String json)
			throws Exception {
		Cookie csrf = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).as("Cookie XSRF-TOKEN").isNotNull();
		builder.cookie(csrf, new Cookie(AuthCookies.ACCESS_TOKEN, jwtTokenProvider.createAccessToken(userId)))
				.header("X-XSRF-TOKEN", csrf.getValue());
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mockMvc.perform(builder);
	}

	private byte[] s3Object(String key) {
		return s3.getObjectAsBytes(b -> b.bucket(TestcontainersConfiguration.BUCKET).key(key)).asByteArray();
	}

	private boolean exists(String key) {
		try {
			s3.headObject(b -> b.bucket(TestcontainersConfiguration.BUCKET).key(key));
			return true;
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				return false;
			}
			throw e;
		}
	}

	/** 左右の 1/4 ずつが赤、真ん中が青の絵（真ん中を切り取ったかを見分ける） */
	private static BufferedImage sides(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.BLUE);
		g.fillRect(width / 4, 0, width / 2, height);
		g.dispose();
		return image;
	}

	private static byte[] png(BufferedImage image) throws IOException {
		return encode(image, "png");
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, out)).as(format).isTrue();
		return out.toByteArray();
	}

}
