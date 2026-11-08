package io.github.keiwebcreate.raisetechtimeline.auth;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.raisetechtimeline.auth.AuthService.AuthResult;
import io.github.keiwebcreate.raisetechtimeline.auth.dto.LoginRequest;
import io.github.keiwebcreate.raisetechtimeline.auth.dto.SignupRequest;
import io.github.keiwebcreate.raisetechtimeline.auth.dto.UserResponse;
import io.github.keiwebcreate.raisetechtimeline.common.error.RefreshTokenInvalidException;
import io.github.keiwebcreate.raisetechtimeline.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 認証の API（docs/features/authentication.md 8）。
 *
 * | POST /api/auth/signup  | 新規登録。2つの Cookie を返す |
 * | POST /api/auth/login   | ログイン。2つの Cookie を返す |
 * | POST /api/auth/refresh | リフレッシュトークンで、2つの Cookie を新しくする |
 * | POST /api/auth/logout  | リフレッシュトークンを取り消し、2つの Cookie を消す |
 * | GET  /api/auth/me      | ログインしている本人 |
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final RefreshTokenService refreshTokenService;
	private final AuthCookies authCookies;
	private final UserMapper userMapper;

	public AuthController(AuthService authService, RefreshTokenService refreshTokenService, AuthCookies authCookies,
			UserMapper userMapper) {
		this.authService = authService;
		this.refreshTokenService = refreshTokenService;
		this.authCookies = authCookies;
		this.userMapper = userMapper;
	}

	@PostMapping("/signup")
	ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
		return withTokens(HttpStatus.CREATED, authService.signup(request));
	}

	@PostMapping("/login")
	ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return withTokens(HttpStatus.OK, authService.login(request, servletRequest.getRemoteAddr()));
	}

	@PostMapping("/refresh")
	ResponseEntity<UserResponse> refresh(
			@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) @Nullable String refreshToken) {
		if (refreshToken == null) {
			throw new RefreshTokenInvalidException("リフレッシュトークンがありません");
		}
		return withTokens(HttpStatus.OK, authService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(
			@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) @Nullable String refreshToken) {
		refreshTokenService.revoke(refreshToken);
		return ResponseEntity.noContent().headers(setCookies(authCookies.clear())).build();
	}

	@GetMapping("/me")
	ResponseEntity<UserResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
		// JWT は正しいが、ユーザーがもういない（退会など）ときは、ログインしていない扱い
		return userMapper.findById(loginUser.id())
				.map(user -> ResponseEntity.ok(UserResponse.from(user)))
				.orElseThrow(() -> new RefreshTokenInvalidException("ユーザーがいません"));
	}

	private ResponseEntity<UserResponse> withTokens(HttpStatus status, AuthResult result) {
		return ResponseEntity.status(status)
				.headers(setCookies(authCookies.issue(result.accessToken(), result.refreshToken())))
				.body(UserResponse.from(result.user()));
	}

	private static HttpHeaders setCookies(List<ResponseCookie> cookies) {
		HttpHeaders headers = new HttpHeaders();
		cookies.forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
		return headers;
	}

}
