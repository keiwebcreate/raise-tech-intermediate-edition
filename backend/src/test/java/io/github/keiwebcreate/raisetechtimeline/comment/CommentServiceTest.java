package io.github.keiwebcreate.raisetechtimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.keiwebcreate.raisetechtimeline.comment.dto.CommentsResponse;
import io.github.keiwebcreate.raisetechtimeline.common.error.CommentNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;

/**
 * CommentService の単体テスト（docs/features/comment.md 3〜5章）。DB（CommentMapper）はにせもの。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

	private static final long ME = 10L;
	private static final long POST_ID = 100L;

	@Mock
	private CommentMapper commentMapper;

	@Mock
	private MediaService mediaService;

	private CommentService commentService;

	@BeforeEach
	void setUp() {
		commentService = new CommentService(commentMapper, mediaService);
		lenient().when(commentMapper.postExists(POST_ID)).thenReturn(true);
		lenient().when(commentMapper.findById(anyLong(), anyLong()))
				.thenAnswer(call -> Optional.of(comment(call.getArgument(0), null)));
	}

	// ===== コメントする（3章・4.1） =====

	@Test
	void コメントは前後の空白を取り除いて保存する() {
		when(commentMapper.insertComment(POST_ID, ME, "なるほど")).thenReturn(1L);

		commentService.create(ME, POST_ID, "　なるほど \n", null);

		verify(commentMapper).insertComment(POST_ID, ME, "なるほど");
	}

	@Test
	void 本文が空なら_コメントを入力してください_280文字を超えたら断る() {
		assertThatThrownBy(() -> commentService.create(ME, POST_ID, "  ", null))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("コメントを入力してください")
				.extracting("field").isEqualTo("content");
		assertThatThrownBy(() -> commentService.create(ME, POST_ID, "あ".repeat(281), null))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("280文字以内で入力してください");
		verify(commentMapper, never()).insertComment(anyLong(), anyLong(), anyString());
	}

	@Test
	void 投稿がなければ_POST_NOT_FOUNDで保存しない() {
		lenient().when(commentMapper.postExists(999L)).thenReturn(false);

		assertThatThrownBy(() -> commentService.create(ME, 999L, "コメント", null))
				.isInstanceOf(PostNotFoundException.class);
		verify(commentMapper, never()).insertComment(anyLong(), anyLong(), anyString());
	}

	// ===== 返信する（4.5） =====

	@Test
	void 返信は_返信先を記録して保存する() {
		when(commentMapper.insertReply(POST_ID, ME, 5L, "返信です")).thenReturn(6L);

		commentService.create(ME, POST_ID, "返信です", 5L);

		verify(commentMapper).insertReply(POST_ID, ME, 5L, "返信です");
		verify(commentMapper, never()).insertComment(anyLong(), anyLong(), anyString());
	}

	@Test
	void 返信先がない_別の投稿のコメントなら_COMMENT_NOT_FOUND() {
		// 返信先が同じ投稿にないときは、保存できない（null が返る）
		when(commentMapper.insertReply(POST_ID, ME, 5L, "返信です")).thenReturn(null);

		assertThatThrownBy(() -> commentService.create(ME, POST_ID, "返信です", 5L))
				.isInstanceOf(CommentNotFoundException.class);
	}

	// ===== 一覧（4.2） =====

	@Test
	void コメントは50件ずつで_51件目があれば_最後のコメントのIDが続きの目印になる() {
		when(commentMapper.findTopLevel(eq(POST_ID), eq(ME), any(), anyInt())).thenReturn(comments(51));

		CommentsResponse page = commentService.comments(ME, POST_ID, null);

		assertThat(page.comments()).hasSize(50);
		assertThat(page.nextAfter()).isEqualTo(50L);
	}

	@Test
	void コメントが50件以下なら_続きはない() {
		when(commentMapper.findTopLevel(eq(POST_ID), eq(ME), any(), anyInt())).thenReturn(comments(3));

		assertThat(commentService.comments(ME, POST_ID, null).nextAfter()).isNull();
	}

	@Test
	void ない投稿のコメント_ないコメントの返信は_404() {
		lenient().when(commentMapper.postExists(999L)).thenReturn(false);
		lenient().when(commentMapper.findTopLevel(eq(999L), anyLong(), any(), anyInt())).thenReturn(List.of());
		assertThatThrownBy(() -> commentService.comments(ME, 999L, null)).isInstanceOf(PostNotFoundException.class);

		lenient().when(commentMapper.commentExists(999L)).thenReturn(false);
		lenient().when(commentMapper.findReplies(eq(999L), anyLong())).thenReturn(List.of());
		assertThatThrownBy(() -> commentService.replies(ME, 999L)).isInstanceOf(CommentNotFoundException.class);
	}

	// ===== 削除（4.4） =====

	@Test
	void 削除は本人のコメントだけ_他人のもの_ないものは_COMMENT_NOT_FOUND() {
		when(commentMapper.delete(1L, ME)).thenReturn(1);
		commentService.delete(ME, 1L);

		when(commentMapper.delete(2L, ME)).thenReturn(0);
		assertThatThrownBy(() -> commentService.delete(ME, 2L)).isInstanceOf(CommentNotFoundException.class);
	}

	// ===== 道具 =====

	private static Comment comment(long id, Long parentId) {
		OffsetDateTime now = OffsetDateTime.parse("2026-11-21T00:00:00Z");
		return new Comment(id, POST_ID, ME, parentId, parentId, "本文", now, "taro", "山田 太郎", null, null, 0, false, 0,
				0, false, false);
	}

	private static List<Comment> comments(int count) {
		return LongStream.rangeClosed(1, count).mapToObj(id -> comment(id, null)).toList();
	}

}
