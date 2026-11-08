package io.github.keiwebcreate.raisetechtimeline.auth.dto;

import io.github.keiwebcreate.raisetechtimeline.user.User;

/**
 * ログインしている本人の情報（新規登録・ログイン・/me の返事）。パスワードのハッシュは返さない。
 */
public record UserResponse(long id, String email, String username, String displayName) {

	public static UserResponse from(User user) {
		return new UserResponse(user.id(), user.email(), user.username(), user.displayName());
	}

}
