package io.github.keiwebcreate.raisetechtimeline.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.keiwebcreate.raisetechtimeline.common.error.CommentNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikeResponse;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikersResponse;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;

/**
 * LikeService の単体テスト（docs/features/like.md 3〜5章）。DB（LikeMapper）はにせもの。
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

	private static final long ME = 10L;
	private static final long POST_ID = 100L;
	private static final long COMMENT_ID = 200L;

	@Mock
	private LikeMapper likeMapper;

	@Mock
	private MediaService mediaService;

	private LikeService likeService;

	@BeforeEach
	void setUp() {
		likeService = new LikeService(likeMapper, mediaService);
		lenient().when(likeMapper.postExists(POST_ID)).thenReturn(true);
		lenient().when(likeMapper.commentExists(COMMENT_ID)).thenReturn(true);
	}

	// ===== 投稿へのいいね（3章・4.1） =====

	@Test
	void いいねすると_いいね済みと今の数を返す() {
		lenient().when(likeMapper.insert(POST_ID, ME)).thenReturn(1);
		when(likeMapper.count(POST_ID)).thenReturn(3L);

		assertThat(likeService.like(ME, POST_ID)).isEqualTo(new LikeResponse(true, 3L));
		verify(likeMapper).insert(POST_ID, ME);
	}

	@Test
	void もういいね済みでも_エラーにせず_いいね済みを返す() {
		// DB の一意制約にぶつかって、足した行が0
		lenient().when(likeMapper.insert(POST_ID, ME)).thenReturn(0);
		when(likeMapper.count(POST_ID)).thenReturn(3L);

		assertThat(likeService.like(ME, POST_ID)).isEqualTo(new LikeResponse(true, 3L));
	}

	@Test
	void 取り消すと_いいねしていないと今の数を返し_していなくてもエラーにしない() {
		lenient().when(likeMapper.delete(POST_ID, ME)).thenReturn(0);
		when(likeMapper.count(POST_ID)).thenReturn(2L);

		assertThat(likeService.unlike(ME, POST_ID)).isEqualTo(new LikeResponse(false, 2L));
		verify(likeMapper).delete(POST_ID, ME);
	}

	@Test
	void ない投稿には_いいねも取り消しも一覧もできず_POST_NOT_FOUND() {
		lenient().when(likeMapper.postExists(999L)).thenReturn(false);

		assertThatThrownBy(() -> likeService.like(ME, 999L)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> likeService.unlike(ME, 999L)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> likeService.likers(ME, 999L, null)).isInstanceOf(PostNotFoundException.class);
		verify(likeMapper, never()).insert(anyLong(), anyLong());
	}

	@Test
	void いいねした人は20件ずつで_21件目があれば_最後のいいねのIDが続きの目印になる() {
		when(likeMapper.findLikers(eq(POST_ID), eq(ME), any(), anyInt())).thenReturn(likers(21));

		LikersResponse page = likeService.likers(ME, POST_ID, null);

		assertThat(page.users()).hasSize(20);
		assertThat(page.nextBefore()).isEqualTo(1000L + 20);
	}

	@Test
	void いいねした人が20人以下なら_続きはない() {
		when(likeMapper.findLikers(eq(POST_ID), eq(ME), any(), anyInt())).thenReturn(likers(20));

		assertThat(likeService.likers(ME, POST_ID, null).nextBefore()).isNull();
	}

	// ===== コメントへのいいね（4.4） =====

	@Test
	void コメントにも_いいね_取り消しができる() {
		lenient().when(likeMapper.insertCommentLike(COMMENT_ID, ME)).thenReturn(1);
		lenient().when(likeMapper.deleteCommentLike(COMMENT_ID, ME)).thenReturn(1);
		when(likeMapper.countCommentLikes(COMMENT_ID)).thenReturn(1L, 0L);

		assertThat(likeService.likeComment(ME, COMMENT_ID)).isEqualTo(new LikeResponse(true, 1L));
		assertThat(likeService.unlikeComment(ME, COMMENT_ID)).isEqualTo(new LikeResponse(false, 0L));
	}

	@Test
	void ないコメントには_いいねも取り消しも一覧もできず_COMMENT_NOT_FOUND() {
		lenient().when(likeMapper.commentExists(999L)).thenReturn(false);

		assertThatThrownBy(() -> likeService.likeComment(ME, 999L)).isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> likeService.unlikeComment(ME, 999L)).isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> likeService.commentLikers(ME, 999L, null))
				.isInstanceOf(CommentNotFoundException.class);
		verify(likeMapper, never()).insertCommentLike(anyLong(), anyLong());
	}

	private static List<Liker> likers(int count) {
		// likeId は 1001, 1002, …（新しい順に並んでいる想定。続きの目印は最後に出した人の likeId）
		return LongStream.rangeClosed(1, count)
				.mapToObj(i -> new Liker(1000L + i, i, "user" + i, "ユーザー" + i, null, null, false, false)).toList();
	}

}
