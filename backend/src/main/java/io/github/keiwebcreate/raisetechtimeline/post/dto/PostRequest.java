package io.github.keiwebcreate.raisetechtimeline.post.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 投稿・編集の入力（docs/features/post.md 3章）。
 * 本文の文字数（前後の空白を取り除いて、絵文字も1文字として1〜280）は PostService で確かめる。
 */
public record PostRequest(
		@NotNull(message = "本文を入力してください")
		String content) {
}
