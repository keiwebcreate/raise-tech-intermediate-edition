package io.github.keiwebcreate.raisetechtimeline.like;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import io.github.keiwebcreate.raisetechtimeline.support.MapperTestConfiguration;
import io.github.keiwebcreate.raisetechtimeline.support.TestData;

/**
 * LikeMapper（likes・comment_likes の SQL）のテスト（docs/features/like.md 3〜4章）。
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MapperTestConfiguration.class)
class LikeMapperTest {

	private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-21T00:00:00Z");

	@Autowired
	private LikeMapper likeMapper;

	@Autowired
	private DataSource dataSource;

	private TestData data;

	private long me;
	private long postId;

	@BeforeEach
	void setUp() {
		data = new TestData(dataSource);
		me = data.user("自分");
		postId = data.post(data.user("投稿した人"), T);
	}

	@Test
	void いいねは1人1回で_2回目は何もしない_自分の投稿にもできる() {
		assertThat(likeMapper.insert(postId, me)).isEqualTo(1);
		assertThat(likeMapper.insert(postId, me)).isZero();
		assertThat(likeMapper.count(postId)).isEqualTo(1);

		long myPost = data.post(me, T);
		assertThat(likeMapper.insert(myPost, me)).isEqualTo(1);
	}

	@Test
	void 取り消しは_いいねしていなくても何もしない() {
		likeMapper.insert(postId, me);

		assertThat(likeMapper.delete(postId, me)).isEqualTo(1);
		assertThat(likeMapper.delete(postId, me)).isZero();
		assertThat(likeMapper.count(postId)).isZero();
	}

	@Test
	void いいねした人は新しくいいねした順で_beforeで続きを読み_見ている人から見たフォローの状態が入る() {
		long a = data.user("先にいいねした人");
		long b = data.user("後でいいねした人");
		likeMapper.insert(postId, a);
		likeMapper.insert(postId, b);
		data.follow(me, b);
		data.follow(a, me);

		List<Liker> likers = likeMapper.findLikers(postId, me, null, 10);

		assertThat(likers).extracting(Liker::userId).containsExactly(b, a);
		assertThat(likers.get(0).following()).isTrue();
		assertThat(likers.get(1).followedBy()).isTrue();
		assertThat(likeMapper.findLikers(postId, me, likers.get(0).likeId(), 10)).extracting(Liker::userId)
				.containsExactly(a);
	}

	@Test
	void 投稿があるかを確かめる() {
		assertThat(likeMapper.postExists(postId)).isTrue();
		assertThat(likeMapper.postExists(-1L)).isFalse();
	}

	// ===== コメントへのいいね（4.4） =====

	@Test
	void コメントにも1人1回いいねでき_投稿のいいねとは別に数える() {
		long comment = data.comment(postId, me, T);
		likeMapper.insert(postId, me);

		assertThat(likeMapper.commentExists(comment)).isTrue();
		assertThat(likeMapper.insertCommentLike(comment, me)).isEqualTo(1);
		assertThat(likeMapper.insertCommentLike(comment, me)).isZero();
		assertThat(likeMapper.countCommentLikes(comment)).isEqualTo(1);
		assertThat(likeMapper.findCommentLikers(comment, me, null, 10)).extracting(Liker::userId).containsExactly(me);

		assertThat(likeMapper.deleteCommentLike(comment, me)).isEqualTo(1);
		assertThat(likeMapper.countCommentLikes(comment)).isZero();
		assertThat(likeMapper.count(postId)).as("投稿のいいねはそのまま").isEqualTo(1);
	}

}
