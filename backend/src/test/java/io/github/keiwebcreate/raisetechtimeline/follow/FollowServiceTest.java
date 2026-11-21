package io.github.keiwebcreate.raisetechtimeline.follow;

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
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.keiwebcreate.raisetechtimeline.common.error.CannotFollowSelfException;
import io.github.keiwebcreate.raisetechtimeline.common.error.UserNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.follow.dto.FollowResponse;
import io.github.keiwebcreate.raisetechtimeline.follow.dto.FollowUsersResponse;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;

/**
 * FollowService の単体テスト（docs/features/profile.md 4章）。DB（FollowMapper）はにせもの。
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

	private static final long ME = 10L;
	private static final long HANAKO = 20L;

	@Mock
	private FollowMapper followMapper;

	@Mock
	private MediaService mediaService;

	private FollowService followService;

	@BeforeEach
	void setUp() {
		followService = new FollowService(followMapper, mediaService);
		lenient().when(followMapper.findUserId("hanako")).thenReturn(Optional.of(HANAKO));
		lenient().when(followMapper.findUserId("taro")).thenReturn(Optional.of(ME));
		lenient().when(followMapper.findUserId("nobody")).thenReturn(Optional.empty());
	}

	@Test
	void フォローすると_フォロー中と相手の今のフォロワー数を返す_ユーザー名の大文字小文字は区別しない() {
		lenient().when(followMapper.insert(ME, HANAKO)).thenReturn(1);
		when(followMapper.countFollowers(HANAKO)).thenReturn(5L);

		assertThat(followService.follow(ME, "Hanako")).isEqualTo(new FollowResponse(true, 5L));
		verify(followMapper).insert(ME, HANAKO);
	}

	@Test
	void もうフォローしていても_エラーにしない() {
		lenient().when(followMapper.insert(ME, HANAKO)).thenReturn(0);
		when(followMapper.countFollowers(HANAKO)).thenReturn(5L);

		assertThat(followService.follow(ME, "hanako")).isEqualTo(new FollowResponse(true, 5L));
	}

	@Test
	void 解除すると_フォローしていないと今の数を返し_していなくてもエラーにしない() {
		lenient().when(followMapper.delete(ME, HANAKO)).thenReturn(0);
		when(followMapper.countFollowers(HANAKO)).thenReturn(4L);

		assertThat(followService.unfollow(ME, "hanako")).isEqualTo(new FollowResponse(false, 4L));
		verify(followMapper).delete(ME, HANAKO);
	}

	@Test
	void 自分自身は_フォローも解除もできない() {
		assertThatThrownBy(() -> followService.follow(ME, "taro")).isInstanceOf(CannotFollowSelfException.class);
		assertThatThrownBy(() -> followService.unfollow(ME, "TARO")).isInstanceOf(CannotFollowSelfException.class);
		verify(followMapper, never()).insert(anyLong(), anyLong());
		verify(followMapper, never()).delete(anyLong(), anyLong());
	}

	@Test
	void いない人は_フォローも解除も一覧も_USER_NOT_FOUND() {
		assertThatThrownBy(() -> followService.follow(ME, "nobody")).isInstanceOf(UserNotFoundException.class);
		assertThatThrownBy(() -> followService.unfollow(ME, "nobody")).isInstanceOf(UserNotFoundException.class);
		assertThatThrownBy(() -> followService.following(ME, "nobody", null)).isInstanceOf(UserNotFoundException.class);
		assertThatThrownBy(() -> followService.followers(ME, "nobody", null)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void 確かめた直後に相手が退会していても_500ではなくUSER_NOT_FOUND() {
		when(followMapper.insert(ME, HANAKO)).thenThrow(new DataIntegrityViolationException("外部キーの違反"));

		assertThatThrownBy(() -> followService.follow(ME, "hanako")).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void 一覧は20件ずつで_21件目があれば_最後のフォローのIDが続きの目印になる() {
		when(followMapper.findFollowing(eq(HANAKO), eq(ME), any(), anyInt())).thenReturn(users(21));

		FollowUsersResponse page = followService.following(ME, "hanako", null);

		assertThat(page.users()).hasSize(20);
		assertThat(page.nextBefore()).isEqualTo(500L + 20);
	}

	@Test
	void フォロワーの一覧が20人以下なら_続きはない() {
		when(followMapper.findFollowers(eq(HANAKO), eq(ME), any(), anyInt())).thenReturn(users(3));

		assertThat(followService.followers(ME, "hanako", null).nextBefore()).isNull();
		verify(followMapper, never()).findFollowing(anyLong(), anyLong(), any(), anyInt());
	}

	private static List<FollowUser> users(int count) {
		return LongStream.rangeClosed(1, count)
				.mapToObj(i -> new FollowUser(500L + i, i, "user" + i, "ユーザー" + i, null, null, false, false)).toList();
	}

}
