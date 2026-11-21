package io.github.keiwebcreate.raisetechtimeline.user;

import java.text.Normalizer;
import java.util.Locale;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;

/**
 * ユーザー検索の言葉（docs/features/user-search.md 3章）。
 *
 * 全角・半角、大文字・小文字を同じとみなすため、言葉を NFKC（Unicode の「見た目が同じ文字を1つにそろえる」決まり。
 * 「ＴＡＲＯ」→「TARO」、半角の「ﾀﾛｳ」→「タロウ」）でそろえてから小文字にする。
 * DB の側も同じく lower(normalize(列, NFKC)) にして比べる（UserMapper.xml の search）。
 *
 * @param text そろえたあとの言葉（ユーザー名との完全一致に使う）
 */
public record UserSearchQuery(String text) {

	/** 検索の言葉の文字数の上限 */
	public static final int MAX_LENGTH = 50;

	private static final String FIELD = "q";

	private static final String MESSAGE_EMPTY = "検索する言葉を入力してください";

	/**
	 * 送られた言葉をそろえ、1〜50文字かを確かめる。前後の空白と、先頭の「@」は取り除く（「@taro」でも「taro」でも同じ）。
	 *
	 * @throws FieldValidationException 空・50文字を超えた（項目は q）
	 */
	public static UserSearchQuery of(String raw) {
		// 文字数は、そろえる前の言葉で数える（画面が数える値と同じにする）。
		// NFKC には文字が増えるものがある（「㍿」1文字 →「株式会社」4文字）ので、そろえたあとで数えると画面と食い違う
		String input = withoutAt(raw);
		if (input.isEmpty()) {
			throw new FieldValidationException(FIELD, MESSAGE_EMPTY);
		}
		// 見た目の1文字を1文字と数える（絵文字も1文字）
		if (input.codePointCount(0, input.length()) > MAX_LENGTH) {
			throw new FieldValidationException(FIELD, "検索する言葉は50文字以内で入力してください");
		}
		// 先頭の「@」（全角の「＠」も）は上の withoutAt で1つだけ取ってある。ここでもう一度取ると「@@taro」が「taro」になり、
		// 画面（searchQuery.ts の searchText）の「@taro」と食い違うので、そろえたあとは前後の空白だけ取る
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
		// NBSP などは strip() では取れないが、NFKC でふつうの空白になる。空のまま検索すると全員が見つかるので、ここでも断る
		if (normalized.isEmpty()) {
			throw new FieldValidationException(FIELD, MESSAGE_EMPTY);
		}
		return new UserSearchQuery(normalized);
	}

	/** 前後の空白と、先頭の「@」（全角の「＠」も）を取り除く */
	private static String withoutAt(String value) {
		String stripped = value.strip();
		if (stripped.startsWith("@") || stripped.startsWith("＠")) {
			stripped = stripped.substring(1).strip();
		}
		return stripped;
	}

	/** 「言葉を含む」の LIKE の形（%言葉%） */
	public String containsPattern() {
		return "%" + escaped() + "%";
	}

	/** 「言葉で始まる」の LIKE の形（言葉%） */
	public String prefixPattern() {
		return escaped() + "%";
	}

	/**
	 * LIKE で特別な意味を持つ記号を、ただの文字として探すようにする（エスケープ）。
	 * LIKE では「%」が「何文字でも」、「_」が「どれか1文字」の意味になる。そのままだと「%」で全員が見つかってしまう。
	 * 前に「\」を付けると、ただの文字になる（SQL の ESCAPE '\'）。「\」そのものも「\\」にする
	 */
	private String escaped() {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
