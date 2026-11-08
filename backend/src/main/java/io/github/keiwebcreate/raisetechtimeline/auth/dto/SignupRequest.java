package io.github.keiwebcreate.raisetechtimeline.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新規登録の入力（docs/features/authentication.md 3.1）。確認用のパスワードは画面だけで確かめる。
 * 表示名の文字数（絵文字も1文字）は AuthService で確かめる。
 */
public record SignupRequest(
		@NotBlank(message = "メールアドレスを入力してください")
		@Email(message = "メールアドレスの形式で入力してください")
		@Size(max = 254, message = "メールアドレスは254文字以内で入力してください")
		String email,

		// 半角の英数字と記号（! から ~ まで）で 8〜64 文字
		@NotNull(message = "パスワードを入力してください")
		@Pattern(regexp = "[\\x21-\\x7E]{8,64}", message = "パスワードは8〜64文字の半角英数字・記号で入力してください")
		String password,

		@NotNull(message = "ユーザー名を入力してください")
		@Pattern(regexp = "[A-Za-z0-9_]{3,15}", message = "ユーザー名は3〜15文字の半角英数字と _ で入力してください")
		String username,

		@NotBlank(message = "表示名を入力してください")
		String displayName) {
}
