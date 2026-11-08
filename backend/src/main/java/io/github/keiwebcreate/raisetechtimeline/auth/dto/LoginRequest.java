package io.github.keiwebcreate.raisetechtimeline.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** ログインの入力（docs/features/authentication.md 3.2） */
public record LoginRequest(
		@NotBlank(message = "メールアドレスを入力してください")
		String email,

		@NotBlank(message = "パスワードを入力してください")
		String password) {
}
