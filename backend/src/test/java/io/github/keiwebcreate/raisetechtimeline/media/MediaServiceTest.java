package io.github.keiwebcreate.raisetechtimeline.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import io.github.keiwebcreate.raisetechtimeline.media.dto.ImageUploadResponse;
import io.github.keiwebcreate.raisetechtimeline.media.dto.VideoUploadResponse;
import io.github.keiwebcreate.raisetechtimeline.support.MutableClock;

/**
 * MediaService の単体テスト（docs/features/post.md 3.2・profile.md 3.2）。
 * S3（MediaStorage）・画像の変換・動画の確かめはにせものにして、送り方と確かめ方の決まりだけを見る。
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

	private static final long ME = 10L;
	private static final long MB = 1024L * 1024;
	private static final String IMAGE_KEY = MediaKeys.newTmpKey(ME, MediaKind.IMAGE);
	private static final String VIDEO_KEY = MediaKeys.newTmpKey(ME, MediaKind.VIDEO);

	@Mock
	private MediaStorage storage;

	@Mock
	private ImageProcessor imageProcessor;

	@Mock
	private VideoInspector videoInspector;

	private MediaService mediaService;

	@BeforeEach
	void setUp() {
		mediaService = new MediaService(storage, imageProcessor, videoInspector,
				new MutableClock(Instant.parse("2026-11-21T03:00:00Z")));
		lenient().when(storage.viewUrl(anyString())).thenAnswer(call -> "https://s3.example/" + call.getArgument(0));
	}

	// ===== 画像（post.md 3.2） =====

	@Test
	void 画像は_WebPにしてから_本人の一時置き場に置き_見るためのURLを返す() throws Exception {
		when(imageProcessor.toWebp(any())).thenReturn(new byte[] {1, 2, 3});

		ImageUploadResponse response = mediaService.uploadImage(ME,
				new MockMultipartFile("file", "my-photo.jpg", "image/jpeg", new byte[] {9, 9}));

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(storage).put(key.capture(), eq(new byte[] {1, 2, 3}), eq("image/webp"));
		assertThat(key.getValue()).startsWith("tmp/10/").endsWith(".webp").doesNotContain("my-photo");
		assertThat(response.key()).isEqualTo(key.getValue());
		assertThat(response.url()).isEqualTo("https://s3.example/" + key.getValue());
	}

	@Test
	void 画像でないものは_S3に置かない() {
		when(imageProcessor.toWebp(any()))
				.thenThrow(new FieldValidationException("file", "JPEG・PNG・WebP・GIF の画像を選んでください"));

		assertThatThrownBy(() -> mediaService.uploadImage(ME,
				new MockMultipartFile("file", "a.txt", "text/plain", new byte[] {1})))
				.isInstanceOf(FieldValidationException.class);
		verify(storage, never()).put(anyString(), any(), anyString());
	}

	// ===== 動画（post.md 3.2） =====

	@Test
	void 動画の送り先は_本人の一時置き場のmp4で_100MBまで() {
		when(storage.uploadUrl(anyString(), eq("video/mp4"), eq(100 * MB))).thenReturn("https://upload.example");

		VideoUploadResponse response = mediaService.prepareVideoUpload(ME, 100 * MB);

		assertThat(response.key()).startsWith("tmp/10/").endsWith(".mp4");
		assertThat(response.uploadUrl()).isEqualTo("https://upload.example");
		assertThatThrownBy(() -> mediaService.prepareVideoUpload(ME, 100 * MB + 1))
				.isInstanceOf(FieldValidationException.class).extracting("field").isEqualTo("size");
	}

	@Test
	void 送ったはずの動画がS3になければ_断る() {
		when(storage.size(VIDEO_KEY)).thenReturn(OptionalLong.empty());

		assertThatThrownBy(() -> mediaService.checkUploaded(VIDEO_KEY))
				.isInstanceOf(FieldValidationException.class).hasMessage(MediaService.MESSAGE_NOT_FOUND);
	}

	@Test
	void 送られた動画が100MBを超えていたら_S3から消して断る() {
		when(storage.size(VIDEO_KEY)).thenReturn(OptionalLong.of(100 * MB + 1));

		assertThatThrownBy(() -> mediaService.checkUploaded(VIDEO_KEY))
				.isInstanceOf(FieldValidationException.class);
		assertThat(deleted()).contains(VIDEO_KEY);
		verify(videoInspector, never()).inspect(any());
	}

	@Test
	void 送られた動画の中身がだめなら_S3から消して断る() {
		when(storage.size(VIDEO_KEY)).thenReturn(OptionalLong.of(10 * MB));
		doThrow(new FieldValidationException("media", "MP4 の動画を選んでください")).when(videoInspector).inspect(any());

		assertThatThrownBy(() -> mediaService.checkUploaded(VIDEO_KEY))
				.isInstanceOf(FieldValidationException.class).hasMessage("MP4 の動画を選んでください");
		assertThat(deleted()).contains(VIDEO_KEY);
	}

	@Test
	void 投稿したら_年と月の正式な場所へコピーする() {
		String postKey = mediaService.copyToPosts(IMAGE_KEY);

		assertThat(postKey).startsWith("posts/2026/11/").endsWith(".webp");
		verify(storage).copy(IMAGE_KEY, postKey);
	}

	@Test
	void 一時置き場のキーが本人のものかを確かめる() {
		assertThat(mediaService.isOwnTmpKey(MediaKeys.newTmpKey(ME, MediaKind.IMAGE), ME)).isTrue();
		assertThat(mediaService.isOwnTmpKey(MediaKeys.newTmpKey(20L, MediaKind.IMAGE), ME)).isFalse();
	}

	// ===== アイコン（profile.md 3.2） =====

	@Test
	void アイコンは_正方形にしてから_本人のアイコン用の一時置き場に置く() {
		when(imageProcessor.toIconWebp(any())).thenReturn(new byte[] {7});

		ImageUploadResponse response = mediaService.uploadIcon(ME,
				new MockMultipartFile("file", "me.png", "image/png", new byte[] {1}));

		assertThat(response.key()).startsWith("tmp/10/icons/").endsWith(".webp");
		verify(storage).put(eq(response.key()), eq(new byte[] {7}), eq("image/webp"));
		verify(imageProcessor, never()).toWebp(any());
	}

	@Test
	void アイコン用でない一時置き場のキー_他人のキーは_アイコンにできない() {
		assertThatThrownBy(() -> mediaService.copyIcon(ME, MediaKeys.newTmpKey(ME, MediaKind.IMAGE)))
				.isInstanceOf(FieldValidationException.class).hasMessage(MediaService.MESSAGE_ICON_NOT_FOUND)
				.extracting("field").isEqualTo("iconKey");
		assertThatThrownBy(() -> mediaService.copyIcon(ME, MediaKeys.newTmpIconKey(20L)))
				.isInstanceOf(FieldValidationException.class);
		verify(storage, never()).copy(anyString(), anyString());
	}

	@Test
	void アイコンは_一時置き場から_iconsの下へコピーする() {
		lenient().when(storage.size(anyString())).thenReturn(OptionalLong.of(1000));
		String tmp = MediaKeys.newTmpIconKey(ME);

		String icon = mediaService.copyIcon(ME, tmp);

		assertThat(icon).startsWith("icons/").endsWith(".webp");
		verify(storage).copy(tmp, icon);
	}

	@Test
	void アイコンがなければURLもない() {
		assertThat(mediaService.iconUrl(null)).isNull();
		verify(storage, never()).viewUrl(anyString());
	}

	/** S3 から消したキー（deleteQuietly に渡したもの） */
	@SuppressWarnings("unchecked")
	private List<String> deleted() {
		ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(storage, atLeast(0)).deleteQuietly(captor.capture());
		return captor.getAllValues().stream().flatMap(Collection::stream).toList();
	}

}
