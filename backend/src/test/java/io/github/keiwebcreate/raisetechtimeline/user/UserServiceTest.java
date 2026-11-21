package io.github.keiwebcreate.raisetechtimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import io.github.keiwebcreate.raisetechtimeline.common.error.UserNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;
import io.github.keiwebcreate.raisetechtimeline.post.PostService;
import io.github.keiwebcreate.raisetechtimeline.user.dto.ProfileUpdateRequest;
import io.github.keiwebcreate.raisetechtimeline.user.dto.UserSearchResponse;

/**
 * UserService の単体テスト（docs/features/profile.md 3章・user-search.md 3章）。
 * DB（UserMapper）・投稿（PostService）・S3（MediaService）はにせもの。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final long ME = 10L;

	@Mock
	private UserMapper userMapper;

	@Mock
	private PostService postService;

	@Mock
	private MediaService mediaService;

	private UserService userService;

	/** 今のアイコンのキー（テストごとに決める） */
	private final AtomicReference<String> currentIcon = new AtomicReference<>();

	@BeforeEach
	void setUp() {
		userService = new UserService(userMapper, postService, mediaService);
		lenient().when(userMapper.findProfileById(eq(ME), anyLong()))
				.thenAnswer(call -> Optional.of(profile(ME, "taro", currentIcon.get())));
		lenient().when(userMapper.updateProfile(anyLong(), anyString(), any(), any())).thenReturn(1);
		lenient().when(mediaService.isIconKey(anyString()))
				.thenAnswer(call -> ((String) call.getArgument(0)).startsWith("icons/"));
	}

	// ===== プロフィールの表示（3.1） =====

	@Test
	void プロフィールは_ユーザー名の大文字小文字を区別せずに探し_自分のものかが分かる() {
		when(userMapper.findProfileByUsername("hanako", ME)).thenReturn(Optional.of(profile(20L, "hanako", null)));

		assertThat(userService.profile(ME, "Hanako").mine()).isFalse();
	}

	@Test
	void いないユーザーは_USER_NOT_FOUND() {
		when(userMapper.findProfileByUsername("nobody", ME)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.profile(ME, "nobody")).isInstanceOf(UserNotFoundException.class);
	}

	// ===== 編集（3.2） =====

	@Test
	void 表示名は前後の空白を取り除き_自己紹介は空なら_なしにする() {
		userService.update(ME, new ProfileUpdateRequest("  山田 太郎　", "   ", null));

		verify(userMapper).updateProfile(ME, "山田 太郎", null, null);
	}

	@Test
	void 表示名が空_50文字を超える_自己紹介が160文字を超えるなら断り_保存しない() {
		assertThatThrownBy(() -> userService.update(ME, new ProfileUpdateRequest(" ", null, null)))
				.isInstanceOf(FieldValidationException.class).extracting("field").isEqualTo("displayName");
		assertThatThrownBy(() -> userService.update(ME, new ProfileUpdateRequest("あ".repeat(51), null, null)))
				.isInstanceOf(FieldValidationException.class).extracting("field").isEqualTo("displayName");
		assertThatThrownBy(() -> userService.update(ME, new ProfileUpdateRequest("太郎", "あ".repeat(161), null)))
				.isInstanceOf(FieldValidationException.class).extracting("field").isEqualTo("bio");
		verify(userMapper, never()).updateProfile(anyLong(), anyString(), any(), any());
	}

	@Test
	void アイコンを残すときは_コピーも削除もしない() {
		currentIcon.set("icons/old.webp");

		userService.update(ME, new ProfileUpdateRequest("太郎", null, "icons/old.webp"));

		verify(userMapper).updateProfile(ME, "太郎", null, "icons/old.webp");
		verify(mediaService, never()).copyIcon(anyLong(), anyString());
		assertThat(keysPassedTo("deleteAfterCommit")).doesNotContain("icons/old.webp");
	}

	@Test
	void アイコンを差し替えると_一時置き場から正式な場所へ移し_前の画像は保存が決まってから消す() {
		currentIcon.set("icons/old.webp");
		when(mediaService.copyIcon(ME, "tmp/10/icons/new.webp")).thenReturn("icons/new.webp");

		userService.update(ME, new ProfileUpdateRequest("太郎", null, "tmp/10/icons/new.webp"));

		verify(userMapper).updateProfile(ME, "太郎", null, "icons/new.webp");
		assertThat(keysPassedTo("deleteAfterCommit")).contains("icons/old.webp", "tmp/10/icons/new.webp");
		// 保存が失敗したら、コピーしたものを消す
		assertThat(keysPassedTo("deleteAfterRollback")).contains("icons/new.webp");
	}

	@Test
	void アイコンを外すと_なしで保存し_前の画像は保存が決まってから消す() {
		currentIcon.set("icons/old.webp");

		userService.update(ME, new ProfileUpdateRequest("太郎", null, null));

		verify(userMapper).updateProfile(ME, "太郎", null, null);
		assertThat(keysPassedTo("deleteAfterCommit")).contains("icons/old.webp");
	}

	// ===== ユーザー検索（user-search.md 3章） =====

	@Test
	void 検索は20件ずつで_21件目があれば_次はoffsetに20を足した位置から() {
		when(userMapper.search(anyString(), anyString(), anyString(), eq(ME), eq(40), anyInt()))
				.thenReturn(searched(21));

		UserSearchResponse page = userService.search(ME, "taro", 40);

		assertThat(page.users()).hasSize(20);
		assertThat(page.nextOffset()).isEqualTo(60);
	}

	@Test
	void 検索の言葉は_そろえてから渡し_記号はエスケープする() {
		when(userMapper.search(anyString(), anyString(), anyString(), eq(ME), eq(0), anyInt())).thenReturn(List.of());

		assertThat(userService.search(ME, " @ＴＡＲＯ_% ", 0).nextOffset()).isNull();
		verify(userMapper).search(eq("taro_%"), eq("taro\\_\\%%"), eq("%taro\\_\\%%"), eq(ME), eq(0), anyInt());
	}

	@Test
	void offsetが負なら断り_検索しない() {
		assertThatThrownBy(() -> userService.search(ME, "taro", -1))
				.isInstanceOf(FieldValidationException.class).extracting("field").isEqualTo("offset");
		verify(userMapper, never()).search(anyString(), anyString(), anyString(), anyLong(), anyInt(), anyInt());
	}

	// ===== 道具 =====

	private static Profile profile(long id, String username, String iconKey) {
		return new Profile(id, username, "名前", null, iconKey, OffsetDateTime.parse("2026-11-01T00:00:00Z"), 0, 0, false,
				false);
	}

	private static List<SearchedUser> searched(int count) {
		return LongStream.rangeClosed(1, count)
				.mapToObj(i -> new SearchedUser(i, "user" + i, "ユーザー" + i, null, null, false, false)).toList();
	}

	/** MediaService の deleteAfterCommit・deleteAfterRollback に渡したキーを、すべて集める */
	@SuppressWarnings("unchecked")
	private List<String> keysPassedTo(String method) {
		ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
		if (method.equals("deleteAfterCommit")) {
			verify(mediaService, atLeast(0)).deleteAfterCommit(captor.capture());
		} else {
			verify(mediaService, atLeast(0)).deleteAfterRollback(captor.capture());
		}
		List<String> keys = new ArrayList<>();
		captor.getAllValues().forEach(keys::addAll);
		return keys;
	}

}
