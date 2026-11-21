package io.github.keiwebcreate.raisetechtimeline.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.keiwebcreate.raisetechtimeline.support.MutableClock;

/**
 * MediaKeys（S3 のキーの作り方と確かめ方）の単体テスト（docs/features/post.md 3.2・profile.md 3.2）。
 * キーはサーバーが決め、利用者が付けたファイル名は使わない。一時置き場には利用者 ID を入れ、他人のものを使わせない。
 */
class MediaKeysTest {

	@Test
	void 一時置き場のキーは_利用者IDの下に_種類の拡張子で_作るたびに違う() {
		String image = MediaKeys.newTmpKey(10L, MediaKind.IMAGE);
		String video = MediaKeys.newTmpKey(10L, MediaKind.VIDEO);

		assertThat(image).startsWith("tmp/10/").endsWith(".webp");
		assertThat(video).startsWith("tmp/10/").endsWith(".mp4");
		assertThat(MediaKeys.newTmpKey(10L, MediaKind.IMAGE)).isNotEqualTo(image);
	}

	@Test
	void 投稿に使える一時置き場のキーは_本人のものだけ() {
		String key = MediaKeys.newTmpKey(10L, MediaKind.IMAGE);

		assertThat(MediaKeys.isTmpKeyOf(key, 10L)).isTrue();
		assertThat(MediaKeys.isTmpKeyOf(key, 20L)).as("他人のキー").isFalse();
		assertThat(MediaKeys.isTmpKeyOf(key.replace("tmp/10/", "tmp/1/"), 10L)).as("ID の一部が同じ").isFalse();
	}

	@Test
	void 投稿に使える一時置き場のキーに_でたらめな形は通さない() {
		assertThat(MediaKeys.isTmpKeyOf("tmp/10/../20/abc.webp", 10L)).as("../ で別の場所を指す").isFalse();
		assertThat(MediaKeys.isTmpKeyOf("tmp/10/my-photo.jpg", 10L)).as("利用者が付けた名前").isFalse();
		assertThat(MediaKeys.isTmpKeyOf("posts/2026/11/abc.webp", 10L)).as("一時置き場ではない").isFalse();
		assertThat(MediaKeys.isTmpKeyOf(MediaKeys.newTmpIconKey(10L), 10L)).as("アイコンの一時置き場").isFalse();
	}

	@Test
	void 投稿の正式な場所は_年と月で分ける() {
		MutableClock clock = new MutableClock(Instant.parse("2026-11-21T03:00:00Z"));

		assertThat(MediaKeys.newPostKey(MediaKind.IMAGE, clock)).startsWith("posts/2026/11/").endsWith(".webp");
		assertThat(MediaKeys.newPostKey(MediaKind.VIDEO, clock)).startsWith("posts/2026/11/").endsWith(".mp4");
	}

	@Test
	void アイコンの一時置き場は_投稿の画像と分け_本人のものだけ使える() {
		String tmpIcon = MediaKeys.newTmpIconKey(10L);

		assertThat(tmpIcon).startsWith("tmp/10/icons/").endsWith(".webp");
		assertThat(MediaKeys.isTmpIconKeyOf(tmpIcon, 10L)).isTrue();
		assertThat(MediaKeys.isTmpIconKeyOf(tmpIcon, 20L)).as("他人のキー").isFalse();
		// 正方形にしていない投稿の画像は、アイコンに使えない
		assertThat(MediaKeys.isTmpIconKeyOf(MediaKeys.newTmpKey(10L, MediaKind.IMAGE), 10L)).isFalse();
	}

	@Test
	void アイコンの正式な場所は_iconsの下() {
		String icon = MediaKeys.newIconKey();

		assertThat(icon).startsWith("icons/").endsWith(".webp");
		assertThat(MediaKeys.isIconKey(icon)).isTrue();
		assertThat(MediaKeys.isIconKey("posts/2026/11/abc.webp")).isFalse();
		assertThat(MediaKeys.isIconKey("icons/../posts/abc.webp")).isFalse();
	}

}
