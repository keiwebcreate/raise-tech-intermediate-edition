package io.github.keiwebcreate.raisetechtimeline.user.dto;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;

/**
 * プロフィールの編集の入力（S-06。docs/features/profile.md 3.2）。文字数は UserService（ProfileText）で確かめる。
 *
 * @param bio 自己紹介。null・空なら「なし」
 * @param iconKey アイコン。今のキーなら「残す」、/api/media/icons で送った一時置き場のキーなら「差し替える」、
 *     null なら「外す」（初期のアイコンに戻す）
 */
public record ProfileUpdateRequest(
		@NotNull(message = "表示名を入力してください")
		String displayName,
		@Nullable String bio,
		@Nullable String iconKey) {
}
