package io.github.keiwebcreate.raisetechtimeline.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException;
import io.github.keiwebcreate.raisetechtimeline.media.dto.ImageUploadResponse;
import io.github.keiwebcreate.raisetechtimeline.media.dto.VideoUploadResponse;

/**
 * 画像・動画の受け付けと、投稿の処理（PostService）から使う S3 の手続き（docs/features/post.md 3.2・4.1）。
 *
 * 流れ：選んだときに一時置き場（tmp/）へ置く → 投稿したら正式な場所（posts/）へコピーし、一時置き場のものを消す。
 * 投稿しなかったものは、S3 のライフサイクルの設定で1日後に消える。
 */
@Service
public class MediaService {

	private static final Logger log = LoggerFactory.getLogger(MediaService.class);

	/** 送ったはずのファイルがない、他人のファイルなど */
	public static final String MESSAGE_NOT_FOUND = "画像・動画が見つかりません。選び直してください";

	private final MediaStorage storage;
	private final ImageProcessor imageProcessor;
	private final VideoInspector videoInspector;
	private final Clock clock;

	public MediaService(MediaStorage storage, ImageProcessor imageProcessor, VideoInspector videoInspector,
			Clock clock) {
		this.storage = storage;
		this.imageProcessor = imageProcessor;
		this.videoInspector = videoInspector;
		this.clock = clock;
	}

	// ===== 選んだとき（POST /api/media/*） =====

	/** 画像を確かめて WebP にし、一時置き場に置く */
	public ImageUploadResponse uploadImage(long userId, MultipartFile file) {
		byte[] webp = imageProcessor.toWebp(bytes(file));
		String key = MediaKeys.newTmpKey(userId, MediaKind.IMAGE);
		storage.put(key, webp, MediaKind.IMAGE.contentType());
		return new ImageUploadResponse(key, storage.viewUrl(key));
	}

	/** 動画を一時置き場へ直接送るための、期限付きの送り先 URL を作る。中身は投稿のときに確かめる */
	public VideoUploadResponse prepareVideoUpload(long userId, long size) {
		if (size > VideoInspector.MAX_BYTES) {
			throw new FieldValidationException("size", VideoInspector.MESSAGE_LIMIT);
		}
		String key = MediaKeys.newTmpKey(userId, MediaKind.VIDEO);
		return new VideoUploadResponse(key, storage.uploadUrl(key, MediaKind.VIDEO.contentType(), size));
	}

	// ===== 投稿のとき（PostService から使う） =====

	/** その利用者が一時置き場に置いたもののキーか（形も確かめる） */
	public boolean isOwnTmpKey(String key, long userId) {
		return MediaKeys.isTmpKeyOf(key, userId);
	}

	/**
	 * 一時置き場のファイルが投稿に使えるかを確かめる。画像は置いたときに確かめ済みなので、あるかだけ見る。
	 * 動画は、大きさ・MP4（H.264）か・長さを確かめ、だめなら S3 から消して断る。
	 */
	public void checkUploaded(String tmpKey) {
		OptionalLong size = storage.size(tmpKey);
		if (size.isEmpty()) {
			throw new FieldValidationException("media", MESSAGE_NOT_FOUND);
		}
		if (MediaKind.fromKey(tmpKey) == MediaKind.VIDEO) {
			try {
				checkVideo(tmpKey, size.getAsLong());
			} catch (FieldValidationException e) {
				storage.deleteQuietly(List.of(tmpKey));
				throw e;
			}
		}
	}

	private void checkVideo(String tmpKey, long size) {
		if (size == 0) {
			throw new FieldValidationException("media", VideoInspector.MESSAGE_TYPE);
		}
		if (size > VideoInspector.MAX_BYTES) {
			throw new FieldValidationException("media", VideoInspector.MESSAGE_LIMIT);
		}
		Path file = null;
		try {
			// ダウンロード先はまだないファイルでないといけないので、作った一時ファイルを先に消す
			file = Files.createTempFile("raisetechtimeline-video-", ".mp4");
			Files.delete(file);
			storage.download(tmpKey, file);
			videoInspector.inspect(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteTempFile(file);
		}
	}

	/** 一時置き場から正式な場所へコピーし、新しいキーを返す（一時置き場のものは、呼ぶ側がコミットのあとに消す） */
	public String copyToPosts(String tmpKey) {
		String key = MediaKeys.newPostKey(MediaKind.fromKey(tmpKey), clock);
		storage.copy(tmpKey, key);
		return key;
	}

	public String viewUrl(String key) {
		return storage.viewUrl(key);
	}

	/**
	 * DB の保存が決まったら（コミットしたら）S3 から消す。
	 * 先に消すと、DB の保存が失敗して元に戻ったとき、DB にはあるのにファイルがない状態になるため。
	 */
	public void deleteAfterCommit(Collection<String> keys) {
		afterTransaction(keys, true);
	}

	/**
	 * DB の保存が失敗して元に戻ったら S3 から消す（コピーしたのに使われなくなったファイル）。
	 * keys は、登録したあとに足してもよい（終わったときの中身を消す）。コピーの途中で失敗したときも、それまでの分を消せる
	 */
	public void deleteAfterRollback(Collection<String> keys) {
		afterTransaction(keys, false);
	}

	// keys は写し取らずに持っておき、トランザクションが終わったときの中身を使う
	private void afterTransaction(Collection<String> keys, boolean onCommit) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				boolean committed = status == STATUS_COMMITTED;
				if (committed == onCommit && !keys.isEmpty()) {
					storage.deleteQuietly(List.copyOf(keys));
				}
			}
		});
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void deleteTempFile(Path file) {
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			log.warn("一時ファイルを消せませんでした: {}", file, e);
		}
	}

}
