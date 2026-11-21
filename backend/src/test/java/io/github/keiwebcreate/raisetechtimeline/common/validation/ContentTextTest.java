package io.github.keiwebcreate.raisetechtimeline.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/**
 * ContentText（投稿・コメントの本文の決まり）の単体テスト（docs/features/post.md 3.1・comment.md 3章）。
 * 見た目の1文字を1文字と数える（画面・サーバー・DB で同じ数え方）。
 */
class ContentTextTest {

	@Test
	void 前後の空白と改行を取り除き_途中の改行は残す() {
		assertThat(ContentText.normalize("\n  一行目\n二行目　\n", "空")).isEqualTo("一行目\n二行目");
	}

	@Test
	void 絵文字も1文字と数え_280文字までよい() {
		assertThat(ContentText.normalize("😀".repeat(280), "空")).hasSize(560);
	}

	@Test
	void 前後の空白は数えない() {
		assertThat(ContentText.normalize("  " + "あ".repeat(280) + "  ", "空")).hasSize(280);
	}

	@Test
	void 本文が281文字なら_280文字以内で入力してください() {
		assertThatThrownBy(() -> ContentText.normalize("あ".repeat(281), "空"))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("280文字以内で入力してください")
				.extracting("field").isEqualTo("content");
	}

	@Test
	void 空_空白だけなら_呼んだ側が決めた文言にする() {
		assertThatThrownBy(() -> ContentText.normalize(" \n　", "コメントを入力してください"))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("コメントを入力してください");
	}

}
