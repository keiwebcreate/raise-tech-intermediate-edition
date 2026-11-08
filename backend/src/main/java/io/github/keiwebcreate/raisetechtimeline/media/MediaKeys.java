package io.github.keiwebcreate.raisetechtimeline.media;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * S3 のキー（ファイルの置き場所の名前）。キーはすべてサーバーが決め、利用者が付けたファイル名は使わない（docs/features/post.md 3.2）。
 *
 * - 一時置き場：tmp/{利用者ID}/{ランダム}.{webp|mp4}。選んだときに置く。投稿しなければ 1日で消える（S3 のライフサイクル）。
 *   利用者 ID を入れるのは、投稿のときに「自分が送ったファイルか」を確かめるため
 * - 正式な場所：posts/{年}/{月}/{ランダム}.{webp|mp4}。投稿したときに一時置き場から移す
 */
final class MediaKeys {

	private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
	// ランダムな部分は UUID（36文字、小文字の16進数とハイフン）
	private static final String RANDOM = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
	private static final Pattern TMP = Pattern.compile("tmp/(\\d+)/" + RANDOM + "\\.(webp|mp4)");

	private MediaKeys() {
	}

	static String newTmpKey(long userId, MediaKind kind) {
		return "tmp/" + userId + "/" + UUID.randomUUID() + "." + kind.extension();
	}

	static String newPostKey(MediaKind kind, Clock clock) {
		LocalDate today = LocalDate.now(clock.withZone(JAPAN));
		return "posts/%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(),
				kind.extension());
	}

	/** その利用者の一時置き場のキーか（形も確かめる。他人のキーや、でたらめな文字は false） */
	static boolean isTmpKeyOf(String key, long userId) {
		var matcher = TMP.matcher(key);
		return matcher.matches() && matcher.group(1).equals(Long.toString(userId));
	}

}
