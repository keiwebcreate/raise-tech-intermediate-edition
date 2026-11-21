package io.github.keiwebcreate.raisetechtimeline.follow;

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
 * FollowMapper（follows の SQL）のテスト（docs/features/profile.md 4章・database-design.md follows）。
 * 本物の PostgreSQL 18 を使う。1つのテストが終わるたびに、入れたデータは取り消される。
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MapperTestConfiguration.class)
class FollowMapperTest {

	@Autowired
	private FollowMapper followMapper;

	@Autowired
	private DataSource dataSource;

	private TestData data;

	@BeforeEach
	void setUp() {
		data = new TestData(dataSource);
	}

	@Test
	void ユーザー名から_その人のIDを探す() {
		long taro = data.user("taro_mapper", "太郎");

		assertThat(followMapper.findUserId("taro_mapper")).contains(taro);
		assertThat(followMapper.findUserId("nobody_mapper")).isEmpty();
	}

	@Test
	void フォローは1回だけ入り_2回目は何もしない() {
		long me = data.user("自分");
		long other = data.user("相手");

		assertThat(followMapper.insert(me, other)).isEqualTo(1);
		assertThat(followMapper.insert(me, other)).as("2回目はエラーにせず0行").isZero();
		assertThat(followMapper.countFollowers(other)).isEqualTo(1);
	}

	@Test
	void 解除は_フォローしていなくても何もしない() {
		long me = data.user("自分");
		long other = data.user("相手");
		data.follow(me, other);

		assertThat(followMapper.delete(me, other)).isEqualTo(1);
		assertThat(followMapper.delete(me, other)).isZero();
		assertThat(followMapper.countFollowers(other)).isZero();
	}

	@Test
	void フォロー中の一覧は_新しくフォローした順で_beforeで続きを読める() {
		long target = data.user("一覧の人");
		long first = data.user("1人目");
		long second = data.user("2人目");
		long third = data.user("3人目");
		data.follow(target, first);
		data.follow(target, second);
		data.follow(target, third);

		List<FollowUser> all = followMapper.findFollowing(target, target, null, 10);
		assertThat(all).extracting(FollowUser::userId).containsExactly(third, second, first);

		List<FollowUser> rest = followMapper.findFollowing(target, target, all.get(0).followId(), 10);
		assertThat(rest).extracting(FollowUser::userId).containsExactly(second, first);
		assertThat(followMapper.findFollowing(target, target, null, 2)).hasSize(2);
	}

	@Test
	void フォロワーの一覧には_見ている人から見たフォローの状態が入る() {
		long viewer = data.user("見ている人");
		long target = data.user("一覧の人");
		long a = data.user("見ている人がフォローしている人");
		long b = data.user("見ている人をフォローしている人");
		data.follow(a, target);
		data.follow(b, target);
		data.follow(viewer, a);
		data.follow(b, viewer);

		List<FollowUser> followers = followMapper.findFollowers(target, viewer, null, 10);

		FollowUser userA = followers.stream().filter(u -> u.userId() == a).findFirst().orElseThrow();
		FollowUser userB = followers.stream().filter(u -> u.userId() == b).findFirst().orElseThrow();
		assertThat(userA.following()).isTrue();
		assertThat(userA.followedBy()).isFalse();
		assertThat(userB.following()).isFalse();
		assertThat(userB.followedBy()).isTrue();
	}

	@Test
	void 自分自身はフォローできない_DBの決まりでも止める() {
		long me = data.user("自分");

		assertThatThrownBy(() -> followMapper.insert(me, me))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
