package io.github.keiwebcreate.raisetechtimeline.auth;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.auth.dto.LoginRequest;
import io.github.keiwebcreate.raisetechtimeline.auth.dto.SignupRequest;
import io.github.keiwebcreate.raisetechtimeline.common.error.EmailDuplicatedException;
import io.github.keiwebcreate.raisetechtimeline.common.error.LoginFailedException;
import io.github.keiwebcreate.raisetechtimeline.common.error.RateLimitedException;
import io.github.keiwebcreate.raisetechtimeline.common.error.RefreshTokenInvalidException;
import io.github.keiwebcreate.raisetechtimeline.common.error.UsernameDuplicatedException;
import io.github.keiwebcreate.raisetechtimeline.common.logging.LogKeys;
import io.github.keiwebcreate.raisetechtimeline.user.ProfileText;
import io.github.keiwebcreate.raisetechtimeline.user.User;
import io.github.keiwebcreate.raisetechtimeline.user.UserMapper;

/**
 * 新規登録・ログイン（docs/features/authentication.md 4.1・4.2）。
 * どちらも成功したら、アクセストークンとリフレッシュトークンを発行する。
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;
	private final LoginRateLimiter loginRateLimiter;
	/** メールアドレスが登録されていないときも、パスワードを照らし合わせる時間をかける（4.2 の補足） */
	private final String dummyPasswordHash;

	public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider,
			RefreshTokenService refreshTokenService, LoginRateLimiter loginRateLimiter) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenService = refreshTokenService;
		this.loginRateLimiter = loginRateLimiter;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	/**
	 * 新規登録。メールアドレス・ユーザー名は小文字にそろえて保存する。
	 *
	 * @throws EmailDuplicatedException 登録済みのメールアドレス
	 * @throws UsernameDuplicatedException 使われているユーザー名
	 */
	@Transactional
	public AuthResult signup(SignupRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String username = request.username().toLowerCase(Locale.ROOT);
		// 表示名の決まりはプロフィールの編集と同じ（ProfileText）
		String displayName = ProfileText.displayName(request.displayName());
		if (userMapper.existsByEmail(email)) {
			throw new EmailDuplicatedException();
		}
		if (userMapper.existsByUsername(username)) {
			throw new UsernameDuplicatedException();
		}
		long userId;
		try {
			userId = userMapper.insert(email, passwordEncoder.encode(request.password()), username, displayName);
		} catch (DuplicateKeyException e) {
			// 上の確かめのすぐあとに、同じメールアドレス・ユーザー名で別の人が登録したとき（同時の登録）
			throw duplicated(e);
		}
		User user = userMapper.findById(userId).orElseThrow();
		return issueTokens(user);
	}

	/**
	 * ログイン。失敗したときは、メールアドレスとパスワードのどちらが違うかを教えない。
	 *
	 * @throws LoginFailedException メールアドレスかパスワードが違う
	 */
	@Transactional
	public AuthResult login(LoginRequest request, String clientIp) {
		try {
			loginRateLimiter.check(clientIp);
		} catch (RateLimitedException e) {
			log.atWarn().addKeyValue(LogKeys.CLIENT_IP, clientIp).log("ログインの失敗が多すぎるため断りました");
			throw e;
		}
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userMapper.findByEmail(email).orElse(null);
		// 登録されていないメールアドレスでも、照らし合わせを1回する（答えが返るまでの時間で、登録の有無を推測させない）
		String hash = user == null ? dummyPasswordHash : user.passwordHash();
		boolean matched = passwordEncoder.matches(request.password(), hash);
		if (user == null || !matched) {
			loginRateLimiter.recordFailure(clientIp);
			// メールアドレスは出さない（docs/logging.md 6章）。同じ接続元からの失敗を追えるように、接続元だけ出す
			log.atWarn().addKeyValue(LogKeys.CLIENT_IP, clientIp).log("ログインに失敗しました");
			throw new LoginFailedException();
		}
		return issueTokens(user);
	}

	/** リフレッシュトークンを取り替えて、新しいアクセストークンも発行する */
	@Transactional(noRollbackFor = RefreshTokenInvalidException.class)
	public AuthResult refresh(String refreshToken) {
		RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
		// 取り替えた直後に本人が退会していたら、500 ではなく「もう更新できない」（REFRESH_INVALID）にする
		User user = userMapper.findById(rotated.userId())
				.orElseThrow(() -> new RefreshTokenInvalidException("ユーザーがいません"));
		return new AuthResult(user, jwtTokenProvider.createAccessToken(user.id()), rotated.token());
	}

	private AuthResult issueTokens(User user) {
		String accessToken = jwtTokenProvider.createAccessToken(user.id());
		return new AuthResult(user, accessToken, refreshTokenService.issue(user.id()));
	}

	private static RuntimeException duplicated(DuplicateKeyException e) {
		String message = String.valueOf(e.getMessage());
		if (message.contains("uq_users_username")) {
			return new UsernameDuplicatedException();
		}
		return new EmailDuplicatedException();
	}

	/**
	 * 発行したトークン。Controller が Cookie に入れる。
	 *
	 * @param user ログインした人
	 * @param accessToken アクセストークン（JWT）
	 * @param refreshToken リフレッシュトークン
	 */
	public record AuthResult(User user, String accessToken, String refreshToken) {
	}

}
