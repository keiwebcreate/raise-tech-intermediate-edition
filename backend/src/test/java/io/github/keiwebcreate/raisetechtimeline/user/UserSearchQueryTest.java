package io.github.keiwebcreate.raisetechtimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/**
 * UserSearchQuery（検索の言葉の整え方）の単体テスト（docs/features/user-search.md 3章・7章）。
 */
class UserSearchQueryTest {

	@Test
	void 全角半角_大文字小文字をそろえ_前後の空白と先頭のatを取り除く() {
		assertThat(UserSearchQuery.of("  ＴＡＲＯ ").text()).isEqualTo("taro");
		assertThat(UserSearchQuery.of("@Taro").text()).isEqualTo("taro");
		assertThat(UserSearchQuery.of("＠ taro").text()).isEqualTo("taro");
		assertThat(UserSearchQuery.of("ﾀﾛｳ").text()).isEqualTo("タロウ");
	}

	@Test
	void 先頭以外のatは残す() {
		assertThat(UserSearchQuery.of("@@taro").text()).isEqualTo("@taro");
	}

	@Test
	void 空_空白だけ_atだけなら断る() {
		for (String q : new String[] {"", "  ", "@", "＠", " @ "}) {
			assertThatThrownBy(() -> UserSearchQuery.of(q))
					.isInstanceOf(FieldValidationException.class)
					.hasMessage("検索する言葉を入力してください");
		}
	}

	@Test
	void そろえたら空白だけになる言葉も_空として断る() {
		// NBSP（改行しない空白）は Java の strip() では取れないが、NFKC でふつうの空白になる。
		// 空のまま検索すると、全員が見つかってしまう（品質チェックで見つかった、2026-11-21）
		for (String q : new String[] {" ", "   ", "@ ", " "}) {
			assertThatThrownBy(() -> UserSearchQuery.of(q))
					.as("「%s」", q)
					.isInstanceOf(FieldValidationException.class)
					.hasMessage("検索する言葉を入力してください");
		}
	}

	@Test
	void 文字数はそろえる前の言葉で数え_50文字まで() {
		// 「㍿」は NFKC で「株式会社」（4文字）になるが、入力は50文字なのでよい
		assertThat(UserSearchQuery.of("㍿".repeat(50)).text()).hasSize(200);
		assertThatThrownBy(() -> UserSearchQuery.of("あ".repeat(51)))
				.isInstanceOf(FieldValidationException.class)
				.hasMessage("検索する言葉は50文字以内で入力してください");
	}

	@Test
	void LIKEの記号はエスケープして_ただの文字として探す() {
		UserSearchQuery query = UserSearchQuery.of("a_b%c\\d");

		assertThat(query.prefixPattern()).isEqualTo("a\\_b\\%c\\\\d%");
		assertThat(query.containsPattern()).isEqualTo("%a\\_b\\%c\\\\d%");
	}

}
