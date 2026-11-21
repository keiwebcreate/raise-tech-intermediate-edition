package io.github.keiwebcreate.raisetechtimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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
 * ユーザー検索の API のテスト（docs/features/user-search.md 3章・8章）。本物の PostgreSQL 18（Testcontainers）を使う。
 * DB はほかのテストと共有なので、テストごとにランダムな目印（token）を名前に入れ、その目印で探す
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserSearchApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SqlCounter sqlCounter;

	// ===== 探し方 =====

	@Test
	void ユーザー名と表示名の_どちらかに言葉を含む人が見つかる() throws Exception {
		String token = token();
		long me = newUser("s_" + token(), "見る 人");
		long byUsername = newUser("a" + token + "b", "名前 その1");
		long byDisplayName = newUser("s_" + token(), "山田" + token + "太郎");
		newUser("s_" + token(), "関係 ない人");

		as(me, search(token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users.length()").value(2))
				.andExpect(jsonPath("$.users[*].id").value(containsInAnyOrder(
						(int) byUsername, (int) byDisplayName)))
				.andExpect(jsonPath("$.nextOffset").isEmpty());
	}

	@Test
	void 大文字小文字_全角半角_先頭の_at_と前後の空白は区別しない() throws Exception {
		String token = token();
		long me = newUser("s_" + token(), "見る 人");
		long byUsername = newUser(token, "名前");
		// 表示名は全角で入れる（「ｑ１２３…」）
		long byDisplayName = newUser("s_" + token(), toFullWidth(token) + "さん");

		for (String q : List.of(token, token.toUpperCase(), toFullWidth(token.toUpperCase()), "@" + token,
				"＠" + token, "  " + token + "　")) {
			as(me, search(q))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.users.length()").value(2))
					.andExpect(jsonPath("$.users[0].id").value(byUsername))
					.andExpect(jsonPath("$.users[1].id").value(byDisplayName));
		}
	}

	@Test
	void 半角カナでも全角カナの名前が見つかる() throws Exception {
		String token = token();
		long me = newUser("s_" + token(), "見る 人");
		long kana = newUser("s_" + token(), "タロウ" + token);

		as(me, search("ﾀﾛｳ" + token))
				.andExpect(jsonPath("$.users.length()").value(1))
				.andExpect(jsonPath("$.users[0].id").value(kana));
	}

	@Test
	void パーセントとアンダースコアは_ただの文字として探す() throws Exception {
		String token = token();
		long me = newUser("s_" + token(), "見る 人");
		long percent = newUser("s_" + token(), token + "%x");
		newUser("s_" + token(), token + "yx");
		long underscore = newUser("s_" + token(), token + "_z");
		newUser("s_" + token(), token + "qz");

		// エスケープしないと「%」は「何文字でも」、「_」は「どれか1文字」になり、2人ずつ見つかってしまう
		as(me, search(token + "%x"))
				.andExpect(jsonPath("$.users.length()").value(1))
				.andExpect(jsonPath("$.users[0].id").value(percent));
		as(me, search(token + "_z"))
				.andExpect(jsonPath("$.users.length()").value(1))
				.andExpect(jsonPath("$.users[0].id").value(underscore));
	}

	// ===== 並び順 =====

	@Test
	void 完全一致_前方一致_部分一致の順で_同じ順位はフォロワーの多い順() throws Exception {
		String token = token();
		long me = newUser("s_" + token(), "見る 人");
		// わざと、あとの順位の人から作る（作った順に並んでいないことを確かめる）
		long containsFew = newUser("x_" + token, "名前");
		long containsMany = newUser("y_" + token, "名前");
		long prefixByName = newUser("s_" + token(), token + "さん");
		long prefixByUsername = newUser(token + "_a", "名前");
		long exact = newUser(token, "名前");
		addFollowers(containsMany, 2);
		addFollowers(containsFew, 1);
		addFollowers(prefixByName, 1);

		as(me, search(token))
				.andExpect(jsonPath("$.users.length()").value(5))
				.andExpect(jsonPath("$.users[0].id").value(exact))
				// 前方一致の2人：フォロワー1人の表示名の人 → 0人のユーザー名の人
				.andExpect(jsonPath("$.users[1].id").value(prefixByName))
				.andExpect(jsonPath("$.users[2].id").value(prefixByUsername))
				// 部分一致の2人：フォロワー2人 → 1人
				.andExpect(jsonPath("$.users[3].id").value(containsMany))
				.andExpect(jsonPath("$.users[4].id").value(containsFew));
	}

	@Test
	void 二十件ずつで_続きはnextOffsetで読む() throws Exception {
		String token = token();
		long me = newUser("s_" + token(), "見る 人");
		for (int i = 0; i < 21; i++) {
			newUser("s_" + token(), token + " " + i);
		}

		String page1 = as(me, search(token))
				.andExpect(jsonPath("$.users.length()").value(20))
				.andExpect(jsonPath("$.nextOffset").value(20))
				.andReturn().getResponse().getContentAsString();
		List<Integer> firstIds = JsonPath.read(page1, "$.users[*].id");

		String page2 = as(me, get("/api/users").param("q", token).param("offset", "20"))
				.andExpect(jsonPath("$.users.length()").value(1))
				.andExpect(jsonPath("$.nextOffset").isEmpty())
				.andReturn().getResponse().getContentAsString();
		List<Integer> secondIds = JsonPath.read(page2, "$.users[*].id");
		assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
	}

	// ===== 1人に出すもの =====

	@Test
	void 結果には自分も出て_フォローの状態と自己紹介が出て_メールアドレスは出ない() throws Exception {
		String token = token();
		long me = newUser(token + "_me", "自分");
		long other = newUser(token + "_ot", "相手");
		jdbcTemplate.update("UPDATE users SET bio = ? WHERE id = ?", "よろしく", other);
		follow(me, other);

		// どちらも前方一致。相手はフォロワーが1人（自分）いるので先に出る
		as(me, search(token))
				.andExpect(jsonPath("$.users.length()").value(2))
				.andExpect(jsonPath("$.users[0].id").value(other))
				.andExpect(jsonPath("$.users[0].username").value(token + "_ot"))
				.andExpect(jsonPath("$.users[0].displayName").value("相手"))
				.andExpect(jsonPath("$.users[0].bio").value("よろしく"))
				.andExpect(jsonPath("$.users[0].iconUrl").isEmpty())
				.andExpect(jsonPath("$.users[0].following").value(true))
				.andExpect(jsonPath("$.users[0].followedBy").value(false))
				.andExpect(jsonPath("$.users[0].email").doesNotExist())
				.andExpect(jsonPath("$.users[1].id").value(me))
				.andExpect(jsonPath("$.users[1].following").value(false));

		// 相手から見ると、自分は「フォローされています」
		as(other, search(token))
				.andExpect(jsonPath("$.users[1].id").value(me))
				.andExpect(jsonPath("$.users[1].following").value(false))
				.andExpect(jsonPath("$.users[1].followedBy").value(true));
	}

	@Test
	void 見つからなければ空の配列() throws Exception {
		long me = newUser("s_" + token(), "見る 人");
		as(me, search(token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users.length()").value(0))
				.andExpect(jsonPath("$.nextOffset").isEmpty());
	}

	// ===== 入力の誤り =====

	@Test
	void 言葉が空_空白だけ_atだけ_51文字なら400() throws Exception {
		long me = newUser("s_" + token(), "見る 人");
		for (String q : List.of("", "   ", "　", "@", " @ ")) {
			as(me, search(q))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.errors[0].field").value("q"))
					.andExpect(jsonPath("$.errors[0].message").value("検索する言葉を入力してください"));
		}
		as(me, search("あ".repeat(51)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].message").value("検索する言葉は50文字以内で入力してください"));
		// 50文字はよい（絵文字も1文字）
		as(me, search("😀".repeat(50))).andExpect(status().isOk());
		// 文字数はそろえる前の言葉で数える。「㍿」は NFKC で「株式会社」（4文字）になるが、50文字なのでよい
		as(me, search("㍿".repeat(50))).andExpect(status().isOk());
		// 先頭の「@」は数えない
		as(me, search("@" + "あ".repeat(50))).andExpect(status().isOk());
	}

	@Test
	void 言葉がない_offsetが負や数でないなら400() throws Exception {
		long me = newUser("s_" + token(), "見る 人");
		as(me, get("/api/users")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		as(me, get("/api/users").param("q", "abc").param("offset", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("offset"));
		as(me, get("/api/users").param("q", "abc").param("offset", "x")).andExpect(status().isBadRequest());
	}

	@Test
	void ログインしていないと検索できない() throws Exception {
		mockMvc.perform(get("/api/users").param("q", "abc"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	// ===== N+1 問題を起こさないこと =====

	@Test
	void 検索のSQLの数は_1人でも20人でも同じ() throws Exception {
		String token = token();
		long me = newUser("s_" + token(), "見る 人");
		addMatchedUser(token, me);
		int one = sqlCountOf(me, token);
		for (int i = 0; i < 19; i++) {
			addMatchedUser(token, me);
		}
		int twenty = sqlCountOf(me, token);

		// 検索（フォロワー数・フォローの状態も）1回
		assertThat(one).as("1人のときの SQL の数").isBetween(1, 2);
		assertThat(twenty).as("20人のときの SQL の数（1人のときは %d）", one).isEqualTo(one);
	}

	// ===== 道具 =====

	/** ほかのテストの人と重ならない目印（英小文字で始まる10文字。ユーザー名にもそのまま使える） */
	private static String token() {
		return "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
	}

	/** 英数字を全角にする（「q1a」→「ｑ１ａ」） */
	private static String toFullWidth(String ascii) {
		StringBuilder sb = new StringBuilder();
		ascii.chars().forEach(c -> sb.append((char) (c + 0xFEE0)));
		return sb.toString();
	}

	private long newUser(String username, String displayName) {
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

	private void addFollowers(long target, int count) {
		for (int i = 0; i < count; i++) {
			follow(newUser("s_" + token(), "フォロワー"), target);
		}
	}

	// 見つかる人を1人足す。アイコン付きで、半分は viewer がフォローし、半分は viewer をフォローする（状態が混ざるように）
	private void addMatchedUser(String token, long viewer) {
		long user = newUser("s_" + token(), token + " さん");
		jdbcTemplate.update("UPDATE users SET icon_image_key = ? WHERE id = ?", "icons/" + UUID.randomUUID() + ".webp",
				user);
		if (user % 2 == 0) {
			follow(viewer, user);
		} else {
			follow(user, viewer);
		}
	}

	private static MockHttpServletRequestBuilder search(String q) {
		return get("/api/users").param("q", q);
	}

	// GET を1回送り、そのあいだに MyBatis が送った SQL の数を返す
	private int sqlCountOf(long userId, String q) throws Exception {
		sqlCounter.reset();
		mockMvc.perform(search(q).cookie(accessCookie(userId))).andExpect(status().isOk());
		return sqlCounter.count();
	}

	private Cookie accessCookie(long userId) {
		return new Cookie(AuthCookies.ACCESS_TOKEN, jwtTokenProvider.createAccessToken(userId));
	}

	private ResultActions as(long userId,
			MockHttpServletRequestBuilder builder) throws Exception {
		return mockMvc.perform(builder.cookie(accessCookie(userId)));
	}

}
