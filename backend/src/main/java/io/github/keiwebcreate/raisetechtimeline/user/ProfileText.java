package io.github.keiwebcreate.raisetechtimeline.user;

import org.jspecify.annotations.Nullable;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/**
 * 表示名と自己紹介の決まり（docs/features/profile.md 3.2・authentication.md）。
 * 新規登録とプロフィールの編集で同じ決まりにするため、ここにまとめる。
 * 見た目の1文字を1文字と数える（絵文字も1文字。DB の varchar と同じ数え方）
 */
public final class ProfileText {

	public static final int DISPLAY_NAME_MAX = 50;
	public static final int BIO_MAX = 160;

	private ProfileText() {
	}

	/**
	 * 前後の空白を取り除き、1〜50文字かを確かめる。
	 *
	 * @throws FieldValidationException 空・50文字を超えた（項目は displayName）
	 */
	public static String displayName(String value) {
		String stripped = value.strip();
		if (stripped.isEmpty()) {
			throw new FieldValidationException("displayName", "表示名を入力してください");
		}
		if (stripped.codePointCount(0, stripped.length()) > DISPLAY_NAME_MAX) {
			throw new FieldValidationException("displayName", "表示名は50文字以内で入力してください");
		}
		return stripped;
	}

	/**
	 * 前後の空白・改行を取り除き、160文字以内かを確かめる。空なら null（自己紹介なし）。途中の改行はそのまま。
	 *
	 * @throws FieldValidationException 160文字を超えた（項目は bio）
	 */
	public static @Nullable String bio(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		if (stripped.isEmpty()) {
			return null;
		}
		if (stripped.codePointCount(0, stripped.length()) > BIO_MAX) {
			throw new FieldValidationException("bio", "自己紹介は160文字以内で入力してください");
		}
		return stripped;
	}

}
