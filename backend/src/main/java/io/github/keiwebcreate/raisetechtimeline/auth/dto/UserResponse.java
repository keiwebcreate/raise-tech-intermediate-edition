package io.github.keiwebcreate.raisetechtimeline.auth.dto;

import org.jspecify.annotations.Nullable;

import io.github.keiwebcreate.raisetechtimeline.user.User;

/**
 * ログインしている本人の情報（新規登録・ログイン・/me の返事）。パスワードのハッシュは返さない。
 *
 * @param iconUrl アイコンを見るための期限付きの URL。未設定なら null（画面のメニューに出す）
 */
public record UserResponse(long id, String email, String username, String displayName, @Nullable String iconUrl) {

	public static UserResponse from(User user, @Nullable String iconUrl) {
		return new UserResponse(user.id(), user.email(), user.username(), user.displayName(), iconUrl);
	}

}
