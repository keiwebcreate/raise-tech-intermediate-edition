package io.github.keiwebcreate.raisetechtimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

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
 * CommentMapper（comments の SQL）のテスト（docs/features/comment.md 4章・database-design.md comments）。
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MapperTestConfiguration.class)
class CommentMapperTest {

	private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-21T00:00:00Z");

	@Autowired
	private CommentMapper commentMapper;

	@Autowired
	private DataSource dataSource;

	private TestData data;

	private long me;
	private long other;
	private long postId;

	@BeforeEach
	void setUp() {
		data = new TestData(dataSource);
		me = data.user("自分");
		other = data.user("ほかの人");
		postId = data.post(other, T);
	}

	@Test
	void 投稿とコメントがあるかを確かめる() {
		long comment = commentMapper.insertComment(postId, me, "コメント");

		assertThat(commentMapper.postExists(postId)).isTrue();
		assertThat(commentMapper.postExists(-1L)).isFalse();
		assertThat(commentMapper.commentExists(comment)).isTrue();
		assertThat(commentMapper.commentExists(-1L)).isFalse();
	}

	// ===== 返信（4.5） =====

	@Test
	void 返信は同じ投稿のコメントにだけでき_返信の返信も一番上のコメントにまとまる() {
		long top = commentMapper.insertComment(postId, me, "一番上");
		long reply = reply(other, top, "返信");
		long replyToReply = reply(me, reply, "返信の返信");

		Comment deep = commentMapper.findById(replyToReply, me).orElseThrow();
		assertThat(deep.parentCommentId()).isEqualTo(reply);
		assertThat(deep.rootCommentId()).as("下に並ぶ場所は一番上のコメント").isEqualTo(top);
	}

	@Test
	void 返信先がない_別の投稿のコメントなら_足さずにnull() {
		long otherPost = data.post(other, T);
		long commentOnOtherPost = commentMapper.insertComment(otherPost, me, "別の投稿のコメント");

		assertThat(commentMapper.insertReply(postId, me, commentOnOtherPost, "返信")).isNull();
		assertThat(commentMapper.insertReply(postId, me, -1L, "返信")).isNull();
		assertThat(data.count("SELECT count(*) FROM comments WHERE post_id = ?", postId)).isZero();
	}

	// ===== 一覧と数（4.2・4.3） =====

	@Test
	void 直接のコメントは古い順で_返信は入らず_afterで続きを読む() {
		long c1 = data.comment(postId, me, T);
		long c2 = data.comment(postId, other, T.plusMinutes(1));
		long c3 = data.comment(postId, me, T.plusMinutes(2));
		commentMapper.insertReply(postId, other, c1, "返信");

		assertThat(ids(commentMapper.findTopLevel(postId, me, null, 10))).containsExactly(c1, c2, c3);
		assertThat(ids(commentMapper.findTopLevel(postId, me, c1, 10))).containsExactly(c2, c3);
		assertThat(ids(commentMapper.findTopLevel(postId, me, null, 2))).containsExactly(c1, c2);
	}

	@Test
	void 返信数は直接の返信だけ_返信を表示の数は下に並ぶ返信すべて() {
		long top = data.comment(postId, me, T);
		long reply = reply(other, top, "返信");
		commentMapper.insertReply(postId, me, reply, "返信の返信");
		commentMapper.insertReply(postId, other, top, "2つ目の返信");

		Comment comment = commentMapper.findTopLevel(postId, me, null, 10).get(0);
		assertThat(comment.replyCount()).as("直接の返信").isEqualTo(2);
		assertThat(comment.threadReplyCount()).as("下に並ぶ返信すべて").isEqualTo(3);
	}

	@Test
	void 返信の一覧は_一番上のコメントの下に並ぶものすべてを古い順に_返信先の名前つきで() {
		long top = data.comment(postId, me, T);
		long r1 = reply(other, top, "返信");
		long r2 = reply(me, r1, "返信の返信");

		List<Comment> replies = commentMapper.findReplies(top, me);

		assertThat(ids(replies)).containsExactly(r1, r2);
		String otherName = data.jdbc().queryForObject("SELECT username FROM users WHERE id = ?", String.class, other);
		assertThat(replies.get(1).replyToUsername()).isEqualTo(otherName);
	}

	@Test
	void コメントのいいね数と_自分がいいねしたか() {
		long c = data.comment(postId, other, T);
		data.jdbc().update("INSERT INTO comment_likes (comment_id, user_id) VALUES (?, ?)", c, me);

		assertThat(commentMapper.findById(c, me).orElseThrow().liked()).isTrue();
		assertThat(commentMapper.findById(c, other).orElseThrow().liked()).isFalse();
		assertThat(commentMapper.findById(c, other).orElseThrow().likeCount()).isEqualTo(1);
	}

	// ===== 削除（4.4） =====

	@Test
	void 消せるのは本人のコメントだけ_消すと下の返信といいねも消える() {
		long top = commentMapper.insertComment(postId, me, "一番上");
		long reply = reply(other, top, "返信");
		commentMapper.insertReply(postId, me, reply, "返信の返信");
		data.jdbc().update("INSERT INTO comment_likes (comment_id, user_id) VALUES (?, ?)", reply, me);

		assertThat(commentMapper.delete(top, other)).isZero();
		assertThat(commentMapper.delete(top, me)).isEqualTo(1);

		assertThat(data.count("SELECT count(*) FROM comments WHERE post_id = ?", postId)).isZero();
		assertThat(data.count("SELECT count(*) FROM comment_likes")).isZero();
	}

	/** 返信を足して、その ID を返す（足せなかったら、そこでテストを失敗にする） */
	private long reply(long userId, long parentId, String content) {
		return Objects.requireNonNull(commentMapper.insertReply(postId, userId, parentId, content), "返信を足せませんでした");
	}

	private static List<Long> ids(List<Comment> comments) {
		return comments.stream().map(Comment::id).toList();
	}

}
