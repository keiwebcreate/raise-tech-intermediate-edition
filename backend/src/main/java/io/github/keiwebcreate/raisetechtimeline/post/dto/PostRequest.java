package io.github.keiwebcreate.raisetechtimeline.post.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;

/**
 * 投稿・編集の入力（docs/features/post.md 3章）。
 * 本文の文字数（前後の空白を取り除いて、絵文字も1文字として1〜280）は PostService で確かめる。
 *
 * @param media 付ける画像・動画のキーを、並べたい順に。新しく選んだものは一時置き場のキー（tmp/...）、
 *     編集で残すものは今のキー（posts/...）。投稿で null・なしは「付けない」。
 *     編集で null（送らない）は「今のまま変えない」、空の並び（[]）は「すべて外す」。
 *     数と組み合わせ（画像4枚まで・動画1本・混ぜない）は PostService で確かめる
 */
public record PostRequest(
		@NotNull(message = "本文を入力してください")
		String content,
		@Nullable List<String> media) {
}
