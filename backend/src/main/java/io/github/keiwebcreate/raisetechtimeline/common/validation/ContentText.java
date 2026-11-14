package io.github.keiwebcreate.raisetechtimeline.common.validation;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/**
 * 投稿とコメントの本文の決まり（要件 2章・docs/features/post.md 3.1・comment.md 3章）。
 * 投稿とコメントで同じ数え方にするため、ここにまとめる。
 */
public final class ContentText {

	/** 本文の文字数の上限 */
	public static final int MAX_LENGTH = 280;

	private static final String MESSAGE_TOO_LONG = "280文字以内で入力してください";

	private ContentText() {
	}

	/**
	 * 前後の空白・改行を取り除き、1〜280文字かを確かめる。
	 * 見た目の1文字を1文字と数える（絵文字も1文字。DB の varchar と同じ数え方）
	 *
	 * @param emptyMessage 空のときの文言（投稿は「本文を入力してください」、コメントは「コメントを入力してください」）
	 * @return 前後の空白を取り除いた本文
	 * @throws FieldValidationException 空・280文字を超えた（項目は content）
	 */
	public static String normalize(String content, String emptyMessage) {
		String value = content.strip();
		if (value.isEmpty()) {
			throw new FieldValidationException("content", emptyMessage);
		}
		if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
			throw new FieldValidationException("content", MESSAGE_TOO_LONG);
		}
		return value;
	}

}
