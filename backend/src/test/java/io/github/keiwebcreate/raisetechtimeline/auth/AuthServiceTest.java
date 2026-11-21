package io.github.keiwebcreate.raisetechtimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.keiwebcreate.raisetechtimeline.auth.dto.LoginRequest;
import io.github.keiwebcreate.raisetechtimeline.auth.dto.SignupRequest;
import io.github.keiwebcreate.raisetechtimeline.common.error.EmailDuplicatedException;
import io.github.keiwebcreate.raisetechtimeline.common.error.LoginFailedException;
import io.github.keiwebcreate.raisetechtimeline.common.error.RateLimitedException;
import io.github.keiwebcreate.raisetechtimeline.common.error.RefreshTokenInvalidException;
import io.github.keiwebcreate.raisetechtimeline.common.error.UsernameDuplicatedException;
import io.github.keiwebcreate.raisetechtimeline.user.User;
import io.github.keiwebcreate.raisetechtimeline.user.UserMapper;

/**
 * AuthService の単体テスト（docs/features/authentication.md 3・4章）。
 * DB（UserMapper）・トークン・回数制限は Mockito の「にせもの」にして、AuthService の判断だけを確かめる。
 * パスワードのハッシュは本物の BCrypt（強さを下げて速くする）を、呼ばれ方を見られるように spy で包んで使う。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String IP = "203.0.113.5";

	@Mock
	private UserMapper userMapper;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private LoginRateLimiter loginRateLimiter;

	private PasswordEncoder passwordEncoder;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		passwordEncoder = spy(new BCryptPasswordEncoder(4));
		authService = new AuthService(userMapper, passwordEncoder, jwtTokenProvider, refreshTokenService,
				loginRateLimiter);
	}

	// ===== 新規登録（4.1） =====

	@Test
	void 新規登録は_メールとユーザー名を小文字に_表示名の空白を取り_パスワードはハッシュで保存し_2つのトークンを返す() {
		when(userMapper.insert(eq("taro@example.com"), anyString(), eq("taro_dev"), eq("山田 太郎"))).thenReturn(10L);
		lenient().when(userMapper.findById(10L)).thenReturn(Optional.of(user(10L, "hash")));
		when(jwtTokenProvider.createAccessToken(10L)).thenReturn("access");
		when(refreshTokenService.issue(10L)).thenReturn("refresh");

		AuthService.AuthResult result = authService
				.signup(new SignupRequest("Taro@Example.com", "password123", "Taro_Dev", "  山田 太郎　"));

		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(userMapper).insert(eq("taro@example.com"), hash.capture(), eq("taro_dev"), eq("山田 太郎"));
		assertThat(hash.getValue()).as("パスワードそのものは保存しない").isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", hash.getValue())).isTrue();
		assertThat(result.user().id()).isEqualTo(10L);
		assertThat(result.accessToken()).isEqualTo("access");
		assertThat(result.refreshToken()).isEqualTo("refresh");
	}

	@Test
	void 登録済みのメールアドレスは_大文字で送っても_EMAIL_DUPLICATEDで_保存しない() {
		lenient().when(userMapper.existsByEmail("taro@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService
				.signup(new SignupRequest("TARO@example.com", "password123", "taro", "太郎")))
				.isInstanceOf(EmailDuplicatedException.class);
		verify(userMapper, never()).insert(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void 登録済みのユーザー名は_大文字で送っても_USERNAME_DUPLICATEDで_保存しない() {
		lenient().when(userMapper.existsByUsername("taro")).thenReturn(true);

		assertThatThrownBy(() -> authService
				.signup(new SignupRequest("taro@example.com", "password123", "TARO", "太郎")))
				.isInstanceOf(UsernameDuplicatedException.class);
		verify(userMapper, never()).insert(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void 同時に同じユーザー名が登録されて一意制約にぶつかっても_同じエラーにする() {
		when(userMapper.insert(anyString(), anyString(), anyString(), anyString())).thenThrow(new DuplicateKeyException(
				"duplicate key value violates unique constraint \"uq_users_username\""));

		assertThatThrownBy(() -> authService
				.signup(new SignupRequest("taro@example.com", "password123", "taro", "太郎")))
				.isInstanceOf(UsernameDuplicatedException.class);
	}

	@Test
	void 同時に同じメールアドレスが登録されて一意制約にぶつかっても_同じエラーにする() {
		when(userMapper.insert(anyString(), anyString(), anyString(), anyString())).thenThrow(new DuplicateKeyException(
				"duplicate key value violates unique constraint \"uq_users_email\""));

		assertThatThrownBy(() -> authService
				.signup(new SignupRequest("taro@example.com", "password123", "taro", "太郎")))
				.isInstanceOf(EmailDuplicatedException.class);
	}

	// ===== ログイン（3.2・4.2） =====

	@Test
	void ログインは_メールの大文字小文字を区別せず_合っていれば2つのトークンを返し_失敗として数えない() {
		String hash = passwordEncoder.encode("password123");
		when(userMapper.findByEmail("taro@example.com")).thenReturn(Optional.of(user(10L, hash)));
		when(jwtTokenProvider.createAccessToken(10L)).thenReturn("access");
		when(refreshTokenService.issue(10L)).thenReturn("refresh");

		AuthService.AuthResult result = authService.login(new LoginRequest("Taro@Example.COM", "password123"), IP);

		assertThat(result.user().id()).isEqualTo(10L);
		assertThat(result.accessToken()).isEqualTo("access");
		assertThat(result.refreshToken()).isEqualTo("refresh");
		verify(loginRateLimiter).check(IP);
		verify(loginRateLimiter, never()).recordFailure(anyString());
	}

	@Test
	void パスワードが違えば_LOGIN_FAILEDで_失敗として数える() {
		// にせものの設定（when …）の中で、別のにせもの（passwordEncoder）を呼ばないよう、先に作っておく
		String hash = passwordEncoder.encode("correct-password");
		when(userMapper.findByEmail("taro@example.com")).thenReturn(Optional.of(user(10L, hash)));

		assertThatThrownBy(() -> authService.login(new LoginRequest("taro@example.com", "wrong-password"), IP))
				.isInstanceOf(LoginFailedException.class);
		verify(loginRateLimiter).recordFailure(IP);
		verify(refreshTokenService, never()).issue(anyLong());
	}

	@Test
	void 登録されていないメールアドレスでも_パスワードの照らし合わせを1回して_同じLOGIN_FAILEDにする() {
		when(userMapper.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123"), IP))
				.isInstanceOf(LoginFailedException.class);
		// 答えが返るまでの時間で、登録の有無を推測させない
		verify(passwordEncoder, times(1)).matches(eq("password123"), anyString());
		verify(loginRateLimiter).recordFailure(IP);
	}

	@Test
	void 失敗が多すぎるときは_パスワードを確かめる前にRATE_LIMITEDで断る() {
		doThrow(new RateLimitedException(120)).when(loginRateLimiter).check(IP);

		assertThatThrownBy(() -> authService.login(new LoginRequest("taro@example.com", "password123"), IP))
				.isInstanceOf(RateLimitedException.class);
		verify(userMapper, never()).findByEmail(anyString());
		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	// ===== ログインの延長（7.3） =====

	@Test
	void 延長は_リフレッシュトークンを取り替え_新しい2つのトークンを返す() {
		when(refreshTokenService.rotate("old-refresh")).thenReturn(new RefreshTokenService.Rotated(10L, "new-refresh"));
		when(userMapper.findById(10L)).thenReturn(Optional.of(user(10L, "hash")));
		when(jwtTokenProvider.createAccessToken(10L)).thenReturn("new-access");

		AuthService.AuthResult result = authService.refresh("old-refresh");

		assertThat(result.user().id()).isEqualTo(10L);
		assertThat(result.accessToken()).isEqualTo("new-access");
		assertThat(result.refreshToken()).isEqualTo("new-refresh");
	}

	@Test
	void 延長のときに本人がもういなければ_REFRESH_INVALIDにする() {
		when(refreshTokenService.rotate("old-refresh")).thenReturn(new RefreshTokenService.Rotated(10L, "new-refresh"));
		when(userMapper.findById(10L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh("old-refresh")).isInstanceOf(RefreshTokenInvalidException.class);
	}

	private static User user(long id, String passwordHash) {
		OffsetDateTime now = OffsetDateTime.parse("2026-11-21T00:00:00Z");
		return new User(id, "taro@example.com", passwordHash, "taro", "山田 太郎", null, null, now, now);
	}

}
