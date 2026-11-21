package io.github.keiwebcreate.raisetechtimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
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
 * PostMapper・PostMediaMapper（posts・post_media の SQL）のテスト（docs/features/timeline.md・post.md・database-design.md）。
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MapperTestConfiguration.class)
class PostMapperTest {

	private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-21T00:00:00Z");

	@Autowired
	private PostMapper postMapper;

	@Autowired
	private PostMediaMapper postMediaMapper;

	@Autowired
	private DataSource dataSource;

	private TestData data;

	private long me;
	private long followed;
	private long stranger;

	@BeforeEach
	void setUp() {
		data = new TestData(dataSource);
		me = data.user("自分");
		followed = data.user("フォローした人");
		stranger = data.user("知らない人");
		data.follow(me, followed);
	}

	// ===== タイムライン（timeline.md） =====

	@Test
	void すべてタブは新しい順で_同じ時刻ならIDの大きい順() {
		long p1 = data.post(stranger, T);
		long p2 = data.post(me, T.plusMinutes(1));
		long p3 = data.post(followed, T.plusMinutes(1));

		assertThat(ids(postMapper.findTimeline(me, false, null, null, 10))).containsExactly(p3, p2, p1);
	}

	@Test
	void フォロー中タブは_自分とフォローした人の投稿だけ() {
		long mine = data.post(me, T);
		long fromFollowed = data.post(followed, T.plusMinutes(1));
		data.post(stranger, T.plusMinutes(2));

		assertThat(ids(postMapper.findTimeline(me, true, null, null, 10))).containsExactly(fromFollowed, mine);
	}

	@Test
	void beforeで続きを_afterで新しいものだけを読む_同じ時刻でも抜けも重なりもない() {
		long p1 = data.post(stranger, T);
		long p2 = data.post(stranger, T.plusMinutes(1));
		long p3 = data.post(stranger, T.plusMinutes(1));
		long p4 = data.post(stranger, T.plusMinutes(2));

		assertThat(ids(postMapper.findTimeline(me, false, null, null, 2))).containsExactly(p4, p3);
		assertThat(ids(postMapper.findTimeline(me, false, p3, null, 10))).containsExactly(p2, p1);
		assertThat(ids(postMapper.findTimeline(me, false, null, p2, 10))).containsExactly(p4, p3);
	}

	@Test
	void 新しい投稿の数は_自分の投稿を数えず_maxで止める() {
		long top = data.post(stranger, T);
		data.post(me, T.plusMinutes(1));
		data.post(followed, T.plusMinutes(2));
		data.post(stranger, T.plusMinutes(3));
		data.post(stranger, T.plusMinutes(4));

		assertThat(postMapper.countNewer(me, false, top, 100)).isEqualTo(3);
		assertThat(postMapper.countNewer(me, true, top, 100)).as("フォロー中タブ").isEqualTo(1);
		assertThat(postMapper.countNewer(me, false, top, 2)).as("max で止める").isEqualTo(2);
		assertThat(postMapper.countNewer(me, false, null, 100)).as("一覧が空ならすべて").isEqualTo(4);
	}

	@Test
	void その人の投稿だけを新しい順に読む() {
		long older = data.post(followed, T);
		data.post(stranger, T.plusMinutes(1));
		long newer = data.post(followed, T.plusMinutes(2));

		assertThat(ids(postMapper.findByAuthor(followed, me, null, 10))).containsExactly(newer, older);
		assertThat(ids(postMapper.findByAuthor(followed, me, newer, 10))).containsExactly(older);
	}

	// ===== 数と状態 =====

	@Test
	void いいね数と自分がいいねしたか_コメント数は直接のコメントだけ() {
		long postId = data.post(stranger, T);
		data.like(postId, me);
		data.like(postId, followed);
		long comment = data.comment(postId, followed, T);
		data.comment(postId, stranger, T.plusMinutes(1));
		// 返信（コメント数には入れない。comment.md 4.3）
		data.jdbc().update("INSERT INTO comments (post_id, user_id, parent_comment_id, root_comment_id, content)"
				+ " VALUES (?, ?, ?, ?, '返信')", postId, me, comment, comment);

		Post seenByMe = postMapper.findById(postId, me).orElseThrow();
		assertThat(seenByMe.likeCount()).isEqualTo(2);
		assertThat(seenByMe.liked()).isTrue();
		assertThat(seenByMe.commentCount()).isEqualTo(2);
		assertThat(postMapper.findById(postId, stranger).orElseThrow().liked()).isFalse();
	}

	@Test
	void ない投稿は空() {
		assertThat(postMapper.findById(-1L, me)).isEmpty();
	}

	// ===== 本人の条件（post.md 4.4・4.5） =====

	@Test
	void 本文を変えられるのは本人だけ_編集した日時が入る() {
		long postId = data.post(me, T);

		assertThat(postMapper.updateContent(postId, stranger, "乗っ取り")).isZero();
		assertThat(postMapper.findById(postId, me).orElseThrow().editedAt()).isNull();

		assertThat(postMapper.updateContent(postId, me, "直した")).isEqualTo(1);
		Post edited = postMapper.findById(postId, me).orElseThrow();
		assertThat(edited.content()).isEqualTo("直した");
		assertThat(edited.editedAt()).isNotNull();
		assertThat(edited.createdAt()).as("投稿した日時は変わらない").isEqualTo(T);
	}

	@Test
	void 消せるのは本人だけ_消すといいね_コメント_画像の行も消える() {
		long postId = data.post(me, T);
		data.like(postId, stranger);
		data.comment(postId, stranger, T);
		postMediaMapper.insert(postId, "image", "posts/2026/11/a.webp", 1);

		assertThat(postMapper.delete(postId, stranger)).isZero();
		assertThat(postMapper.delete(postId, me)).isEqualTo(1);

		assertThat(data.count("SELECT count(*) FROM likes WHERE post_id = ?", postId)).isZero();
		assertThat(data.count("SELECT count(*) FROM comments WHERE post_id = ?", postId)).isZero();
		assertThat(data.count("SELECT count(*) FROM post_media WHERE post_id = ?", postId)).isZero();
	}

	// ===== 画像・動画（post_media） =====

	@Test
	void 画像は投稿ごと_並び順に_まとめて1回で読む() {
		long a = data.post(me, T);
		long b = data.post(me, T.plusMinutes(1));
		postMediaMapper.insert(a, "image", "a2", 2);
		postMediaMapper.insert(a, "image", "a1", 1);
		postMediaMapper.insert(b, "video", "b1", 1);

		List<PostMedia> media = postMediaMapper.findByPostIds(List.of(a, b));

		assertThat(media).extracting(PostMedia::s3Key).containsExactly("a1", "a2", "b1");
		assertThat(media.get(2).mediaType()).isEqualTo("video");
	}

	@Test
	void 並べ直すときは_その投稿の画像の行だけを消す() {
		long a = data.post(me, T);
		long b = data.post(me, T);
		postMediaMapper.insert(a, "image", "a1", 1);
		postMediaMapper.insert(b, "image", "b1", 1);

		postMediaMapper.deleteByPostId(a);

		assertThat(postMediaMapper.findByPostIds(List.of(a, b))).extracting(PostMedia::s3Key).containsExactly("b1");
	}

	// PostgreSQL は、トランザクションの中で一度エラーが起きると、そのあとの SQL をすべて断る。
	// 1つのテストは1つのトランザクションなので、DB の決まりの違反は1つのテストで1つだけ確かめる

	@Test
	void 同じ投稿に同じ並び順は2つない_DBの決まり() {
		long a = data.post(me, T);
		postMediaMapper.insert(a, "image", "a1", 1);

		assertThatThrownBy(() -> postMediaMapper.insert(a, "image", "a1-again", 1))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 並び順は1から4まで_画像は1つの投稿に4つまで_DBの決まり() {
		long a = data.post(me, T);

		assertThatThrownBy(() -> postMediaMapper.insert(a, "image", "a5", 5))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static List<Long> ids(List<Post> posts) {
		return posts.stream().map(Post::id).toList();
	}

}
