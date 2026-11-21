package io.github.keiwebcreate.raisetechtimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/**
 * ProfileText（表示名・自己紹介の決まり）の単体テスト（docs/features/authentication.md 3.1・profile.md 3.2）。
 */
class ProfileTextTest {

	@Test
	void 表示名は前後の空白を取り除き_1から50文字() {
		assertThat(ProfileText.displayName("　山田 太郎 ")).isEqualTo("山田 太郎");
		assertThat(ProfileText.displayName("😀".repeat(50))).isNotEmpty();
	}

	@Test
	void 表示名が空_51文字なら断る() {
		assertThatThrownBy(() -> ProfileText.displayName("   "))
				.isInstanceOf(FieldValidationException.class).hasMessage("表示名を入力してください");
		assertThatThrownBy(() -> ProfileText.displayName("あ".repeat(51)))
				.isInstanceOf(FieldValidationException.class).hasMessage("表示名は50文字以内で入力してください");
	}

	@Test
	void 自己紹介は_空ならなし_途中の改行は残し_160文字まで() {
		assertThat(ProfileText.bio(null)).isNull();
		assertThat(ProfileText.bio("  \n ")).isNull();
		assertThat(ProfileText.bio("\nJava を勉強中です。\nよろしく\n")).isEqualTo("Java を勉強中です。\nよろしく");
		assertThat(ProfileText.bio("😀".repeat(160))).isNotNull();
	}

	@Test
	void 自己紹介が161文字なら断る() {
		assertThatThrownBy(() -> ProfileText.bio("あ".repeat(161)))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("自己紹介は160文字以内で入力してください")
				.extracting("field").isEqualTo("bio");
	}

}
