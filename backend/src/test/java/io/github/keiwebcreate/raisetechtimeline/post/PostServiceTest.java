package io.github.keiwebcreate.raisetechtimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import io.github.keiwebcreate.raisetechtimeline.common.error.PostNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;
import io.github.keiwebcreate.raisetechtimeline.post.dto.PostResponse;
import io.github.keiwebcreate.raisetechtimeline.post.dto.TimelineResponse;

/**
 * PostService の単体テスト（docs/features/post.md 3〜5章・timeline.md）。
 * DB（PostMapper・PostMediaMapper）と S3（MediaService）はにせものにして、投稿の決まりだけを確かめる。
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

	private static final long ME = 10L;
	private static final long OTHER = 20L;
	private static final long POST_ID = 100L;

	@Mock
	private PostMapper postMapper;

	@Mock
	private PostMediaMapper postMediaMapper;

	@Mock
	private MediaService mediaService;

	private PostService postService;

	@BeforeEach
	void setUp() {
		postService = new PostService(postMapper, postMediaMapper, mediaService);
		// 返事を作るための読み直し（どの順で呼ばれても困らないよう、緩く決めておく）
		lenient().when(postMapper.findById(anyLong(), anyLong())).thenAnswer(call -> Optional
				.of(post(call.getArgument(0), ME)));
		lenient().when(postMediaMapper.findByPostIds(any())).thenReturn(List.of());
		// 自分（ID 10）の一時置き場は tmp/10/ の下（docs/features/post.md 3.2）
		lenient().when(mediaService.isOwnTmpKey(anyString(), anyLong()))
				.thenAnswer(call -> ((String) call.getArgument(0)).startsWith("tmp/" + call.getArgument(1) + "/"));
		lenient().when(mediaService.copyToPosts(anyString()))
				.thenAnswer(call -> "posts/2026/11/" + ((String) call.getArgument(0)).substring("tmp/10/".length()));
	}

	// ===== 本文（3章・3.1） =====

	@Test
	void 本文は前後の空白と改行を取り除いて保存する() {
		when(postMapper.insert(ME, "こんにちは\n世界")).thenReturn(POST_ID);

		postService.create(ME, "  \nこんにちは\n世界　\n", List.of());

		verify(postMapper).insert(ME, "こんにちは\n世界");
	}

	@Test
	void 本文が空_空白だけなら_本文を入力してください() {
		for (String content : List.of("", "   ", "\n　\n")) {
			assertThatThrownBy(() -> postService.create(ME, content, List.of()))
					.isInstanceOf(FieldValidationException.class)
					.hasMessage("本文を入力してください")
					.extracting("field").isEqualTo("content");
		}
		verify(postMapper, never()).insert(anyLong(), anyString());
	}

	@Test
	void 本文は絵文字も1文字と数え_280文字まで() {
		when(postMapper.insert(eq(ME), anyString())).thenReturn(POST_ID);
		postService.create(ME, "😀".repeat(280), List.of());

		assertThatThrownBy(() -> postService.create(ME, "あ".repeat(281), List.of()))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("280文字以内で入力してください");
	}

	// ===== 画像・動画（3章・3.2） =====

	@Test
	void 画像は4枚まで_選んだ順に並べ_一時置き場から正式な場所へ移す() {
		when(postMapper.insert(eq(ME), anyString())).thenReturn(POST_ID);
		List<String> keys = List.of("tmp/10/a.webp", "tmp/10/b.webp", "tmp/10/c.webp", "tmp/10/d.webp");

		postService.create(ME, "画像4枚", keys);

		for (int i = 0; i < keys.size(); i++) {
			String moved = "posts/2026/11/" + keys.get(i).substring("tmp/10/".length());
			// 並び順は 1〜4（docs/database-design.md post_media）
			verify(postMediaMapper).insert(POST_ID, "image", moved, i + 1);
		}
	}

	@Test
	void 画像が5枚なら_画像は4枚までです() {
		List<String> keys = List.of("tmp/10/a.webp", "tmp/10/b.webp", "tmp/10/c.webp", "tmp/10/d.webp",
				"tmp/10/e.webp");

		assertThatThrownBy(() -> postService.create(ME, "画像5枚", keys))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("画像は4枚までです")
				.extracting("field").isEqualTo("media");
		verify(postMapper, never()).insert(anyLong(), anyString());
	}

	@Test
	void 画像と動画は混ぜられない() {
		assertThatThrownBy(() -> postService.create(ME, "混ぜる", List.of("tmp/10/a.webp", "tmp/10/v.mp4")))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("画像と動画は一緒に投稿できません");
		verify(postMapper, never()).insert(anyLong(), anyString());
	}

	@Test
	void 動画は1本まで() {
		assertThatThrownBy(() -> postService.create(ME, "動画2本", List.of("tmp/10/v1.mp4", "tmp/10/v2.mp4")))
				.isInstanceOf(FieldValidationException.class)
				.extracting("field").isEqualTo("media");
		verify(postMapper, never()).insert(anyLong(), anyString());
	}

	@Test
	void 動画は_投稿のときにS3の中身を確かめてから_正式な場所へ移す() {
		when(postMapper.insert(eq(ME), anyString())).thenReturn(POST_ID);

		postService.create(ME, "動画", List.of("tmp/10/v.mp4"));

		verify(mediaService).checkUploaded("tmp/10/v.mp4");
		verify(postMediaMapper).insert(POST_ID, "video", "posts/2026/11/v.mp4", 1);
	}

	@Test
	void 他人が置いたファイルは使えない() {
		assertThatThrownBy(() -> postService.create(ME, "他人の画像", List.of("tmp/20/a.webp")))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage(MediaService.MESSAGE_NOT_FOUND);
		verify(mediaService, never()).copyToPosts(anyString());
	}

	@Test
	void 一時置き場のファイルは_DBの保存が決まってから消し_コピーしたものは失敗したら消す() {
		when(postMapper.insert(eq(ME), anyString())).thenReturn(POST_ID);

		postService.create(ME, "画像", List.of("tmp/10/a.webp"));

		assertThat(keysPassedTo("deleteAfterCommit")).contains("tmp/10/a.webp");
		assertThat(keysPassedTo("deleteAfterRollback")).contains("posts/2026/11/a.webp");
	}

	// ===== 詳細・編集・削除（4.3〜4.5） =====

	@Test
	void ない投稿は_POST_NOT_FOUND() {
		when(postMapper.findById(POST_ID, ME)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> postService.get(ME, POST_ID)).isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void 他人の投稿は編集できず_POST_NOT_FOUNDで_何も変えない() {
		lenient().when(postMapper.findById(POST_ID, OTHER)).thenReturn(Optional.of(post(POST_ID, ME)));
		lenient().when(postMapper.updateContent(eq(POST_ID), eq(OTHER), anyString())).thenReturn(0);

		assertThatThrownBy(() -> postService.update(OTHER, POST_ID, "乗っ取り", null))
				.isInstanceOf(PostNotFoundException.class);
		verify(postMediaMapper, never()).deleteByPostId(anyLong());
		verify(mediaService, never()).deleteAfterCommit(any());
	}

	@Test
	void 編集で画像を送らなければ_今の画像のまま() {
		lenient().when(postMapper.updateContent(POST_ID, ME, "直した")).thenReturn(1);

		postService.update(ME, POST_ID, "直した", null);

		verify(postMapper).updateContent(POST_ID, ME, "直した");
		verify(postMediaMapper, never()).deleteByPostId(anyLong());
		verify(postMediaMapper, never()).insert(anyLong(), anyString(), anyString(), anyInt());
	}

	@Test
	void 編集で外した画像は_S3からも消し_残した画像はそのまま_足した画像は正式な場所へ移す() {
		lenient().when(postMapper.updateContent(POST_ID, ME, "直した")).thenReturn(1);
		when(postMediaMapper.findByPostIds(List.of(POST_ID))).thenReturn(List.of(
				new PostMedia(POST_ID, "image", "posts/2026/11/keep.webp", 1),
				new PostMedia(POST_ID, "image", "posts/2026/11/drop.webp", 2)));

		postService.update(ME, POST_ID, "直した", List.of("posts/2026/11/keep.webp", "tmp/10/new.webp"));

		verify(postMediaMapper).insert(POST_ID, "image", "posts/2026/11/keep.webp", 1);
		verify(postMediaMapper).insert(POST_ID, "image", "posts/2026/11/new.webp", 2);
		assertThat(keysPassedTo("deleteAfterCommit")).contains("posts/2026/11/drop.webp", "tmp/10/new.webp")
				.doesNotContain("posts/2026/11/keep.webp");
	}

	@Test
	void 編集で_ほかの投稿の画像のキーを送っても使えない() {
		lenient().when(postMapper.updateContent(POST_ID, ME, "直した")).thenReturn(1);
		lenient().when(postMediaMapper.findByPostIds(List.of(POST_ID))).thenReturn(List.of());

		assertThatThrownBy(() -> postService.update(ME, POST_ID, "直した", List.of("posts/2026/11/someone-else.webp")))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage(MediaService.MESSAGE_NOT_FOUND);
	}

	@Test
	void 削除は本人の投稿だけで_画像はDBの削除が決まってからS3から消す() {
		lenient().when(postMediaMapper.findByPostIds(List.of(POST_ID)))
				.thenReturn(List.of(new PostMedia(POST_ID, "image", "posts/2026/11/a.webp", 1)));
		when(postMapper.delete(POST_ID, ME)).thenReturn(1);

		postService.delete(ME, POST_ID);

		assertThat(keysPassedTo("deleteAfterCommit")).contains("posts/2026/11/a.webp");
	}

	@Test
	void 他人の投稿_ない投稿は削除できず_POST_NOT_FOUNDで_S3も消さない() {
		lenient().when(postMapper.findById(POST_ID, OTHER)).thenReturn(Optional.of(post(POST_ID, ME)));
		lenient().when(postMapper.delete(POST_ID, OTHER)).thenReturn(0);

		assertThatThrownBy(() -> postService.delete(OTHER, POST_ID)).isInstanceOf(PostNotFoundException.class);
		assertThat(keysPassedTo("deleteAfterCommit")).isEmpty();
	}

	// ===== タイムライン（timeline.md） =====

	@Test
	void タイムラインは20件ずつで_21件目があれば続きがある() {
		when(postMapper.findTimeline(eq(ME), anyBoolean(), any(), any(), anyInt())).thenReturn(posts(21));

		TimelineResponse page = postService.timeline(ME, TimelineTab.ALL, null, null);

		assertThat(page.posts()).hasSize(20);
		assertThat(page.hasMore()).isTrue();
	}

	@Test
	void タイムラインが20件以下なら_続きはない() {
		when(postMapper.findTimeline(eq(ME), anyBoolean(), any(), any(), anyInt())).thenReturn(posts(20));

		assertThat(postService.timeline(ME, TimelineTab.ALL, null, null).hasMore()).isFalse();
	}

	@Test
	void 画像は20件ぶんまとめて1回で読む_N足す1問題にしない() {
		when(postMapper.findTimeline(eq(ME), anyBoolean(), any(), any(), anyInt())).thenReturn(posts(20));

		postService.timeline(ME, TimelineTab.ALL, null, null);

		verify(postMediaMapper, times(1)).findByPostIds(any());
	}

	@Test
	void フォロー中タブでは_フォローした人と自分の投稿だけを読む() {
		when(postMapper.findTimeline(eq(ME), eq(true), any(), any(), anyInt())).thenReturn(List.of());
		postService.timeline(ME, TimelineTab.FOLLOWING, null, null);
		verify(postMapper).findTimeline(eq(ME), eq(true), any(), any(), anyInt());

		when(postMapper.findTimeline(eq(ME), eq(false), any(), any(), anyInt())).thenReturn(List.of());
		postService.timeline(ME, TimelineTab.ALL, null, null);
		verify(postMapper).findTimeline(eq(ME), eq(false), any(), any(), anyInt());
	}

	@Test
	void 返事の投稿には_自分の投稿かどうかが入る() {
		when(postMapper.findTimeline(eq(ME), anyBoolean(), any(), any(), anyInt()))
				.thenReturn(List.of(post(1L, ME), post(2L, OTHER)));

		List<PostResponse> posts = postService.timeline(ME, TimelineTab.ALL, null, null).posts();

		assertThat(posts).extracting(PostResponse::mine).containsExactly(true, false);
	}

	// ===== 道具 =====

	private static Post post(long id, long authorId) {
		OffsetDateTime now = OffsetDateTime.parse("2026-11-21T00:00:00Z");
		return new Post(id, authorId, "本文", null, now, "taro", "山田 太郎", null, 0, false, 0);
	}

	private static List<Post> posts(int count) {
		return LongStream.rangeClosed(1, count).mapToObj(id -> post(id, OTHER)).toList();
	}

	/** MediaService の deleteAfterCommit・deleteAfterRollback に渡したキーを、すべて集める */
	@SuppressWarnings("unchecked")
	private List<String> keysPassedTo(String method) {
		ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
		List<String> keys = new ArrayList<>();
		try {
			if (method.equals("deleteAfterCommit")) {
				verify(mediaService, atLeast(0)).deleteAfterCommit(captor.capture());
			} else {
				verify(mediaService, atLeast(0)).deleteAfterRollback(captor.capture());
			}
		} catch (AssertionError e) {
			return keys;
		}
		captor.getAllValues().forEach(keys::addAll);
		return keys;
	}

}
