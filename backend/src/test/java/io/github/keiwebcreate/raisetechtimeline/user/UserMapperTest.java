package io.github.keiwebcreate.raisetechtimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.keiwebcreate.raisetechtimeline.support.MapperTestConfiguration;
import io.github.keiwebcreate.raisetechtimeline.support.TestData;

/**
 * UserMapper（users の SQL）のテスト（docs/features/profile.md 3章・user-search.md 3章）。
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MapperTestConfiguration.class)
class UserMapperTest {

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private DataSource dataSource;

	private TestData data;

	@BeforeEach
	void setUp() {
		data = new TestData(dataSource);
	}

	// ===== 登録・ログイン =====

	@Test
	void 登録したユーザーを_ID_メールアドレスで探せ_登録済みかを確かめられる() {
		long id = userMapper.insert("taro@example.com", "hash", "taro_um", "太郎");

		assertThat(userMapper.findById(id)).get().extracting(User::username).isEqualTo("taro_um");
		assertThat(userMapper.findByEmail("taro@example.com")).get().extracting(User::id).isEqualTo(id);
		assertThat(userMapper.existsByEmail("taro@example.com")).isTrue();
		assertThat(userMapper.existsByUsername("taro_um")).isTrue();
		assertThat(userMapper.existsByUsername("hanako_um")).isFalse();
	}

	@Test
	void ユーザー名は小文字の英数字と_だけ_DBの決まりでも止める() {
		assertThatThrownBy(() -> userMapper.insert("a@example.com", "hash", "Taro", "太郎"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ===== プロフィール（3.1） =====

	@Test
	void プロフィールには_フォロー数とフォロワー数と_見ている人から見たフォローの状態が入る() {
		long taro = data.user("taro_um2", "太郎");
		long viewer = data.user("見ている人");
		long x = data.user("x");
		data.follow(taro, x);
		data.follow(x, taro);
		data.follow(viewer, taro);

		Profile profile = userMapper.findProfileByUsername("taro_um2", viewer).orElseThrow();

		assertThat(profile.id()).isEqualTo(taro);
		assertThat(profile.followingCount()).isEqualTo(1);
		assertThat(profile.followerCount()).isEqualTo(2);
		assertThat(profile.following()).isTrue();
		assertThat(profile.followedBy()).isFalse();
		assertThat(userMapper.findProfileByUsername("nobody_um", viewer)).isEmpty();
	}

	@Test
	void プロフィールの表示名_自己紹介_アイコンを変える() {
		long taro = data.user("太郎");

		assertThat(userMapper.updateProfile(taro, "山田 太郎", "よろしく", "icons/a.webp")).isEqualTo(1);

		Profile profile = userMapper.findProfileById(taro, taro).orElseThrow();
		assertThat(profile.displayName()).isEqualTo("山田 太郎");
		assertThat(profile.bio()).isEqualTo("よろしく");
		assertThat(profile.iconImageKey()).isEqualTo("icons/a.webp");
	}

	// ===== ユーザー検索（user-search.md 3章） =====

	@Test
	void 検索は_ユーザー名と表示名の部分一致で_全角半角_大文字小文字をそろえる() {
		long byUsername = data.user("xqz_taro", "名前");
		long byFullWidthName = data.user("ＸＱＺ太郎");
		data.user("関係ない人");

		assertThat(userIds(search("XQZ"))).containsExactlyInAnyOrder(byUsername, byFullWidthName);
	}

	@Test
	void 完全一致_前方一致_部分一致の順で_同じ順位はフォロワーの多い順_それも同じならIDの順() {
		long contains1 = data.user("a_qzk", "名前");
		long contains2 = data.user("b_qzk", "名前");
		long prefixByName = data.user("qzkさん");
		long prefix = data.user("qzk_a", "名前");
		long exact = data.user("qzk", "名前");
		data.follow(data.user("f"), contains2);

		assertThat(userIds(search("qzk"))).containsExactly(exact, prefixByName, prefix, contains2, contains1);
	}

	@Test
	void パーセントとアンダースコアは_ただの文字として探す() {
		long withPercent = data.user("割引50%オフ");
		data.user("割引50円オフ");

		assertThat(userIds(search("50%"))).containsExactly(withPercent);
	}

	@Test
	void 検索はoffsetとlimitで続きを読む() {
		for (int i = 0; i < 5; i++) {
			data.user("zqx" + i, "名前");
		}

		assertThat(search("zqx", 0, 3)).hasSize(3);
		assertThat(search("zqx", 3, 3)).hasSize(2);
	}

	private List<SearchedUser> search(String q) {
		return search(q, 0, 50);
	}

	private List<SearchedUser> search(String q, int offset, int limit) {
		UserSearchQuery query = UserSearchQuery.of(q);
		long viewer = data.user("検索する人");
		return userMapper.search(query.text(), query.prefixPattern(), query.containsPattern(), viewer, offset, limit);
	}

	private static List<Long> userIds(List<SearchedUser> users) {
		return users.stream().map(SearchedUser::userId).toList();
	}

}
