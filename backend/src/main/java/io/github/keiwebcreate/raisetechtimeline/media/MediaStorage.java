package io.github.keiwebcreate.raisetechtimeline.media;

import java.nio.file.Path;
import java.util.Collection;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 の読み書き。S3 を直接触るのはこのクラスだけにする（ほかのクラスは S3 の SDK を知らなくてよい）。
 * バケットは公開しないので、画面には期限付きの URL（署名付き URL）を渡す。
 */
@Component
public class MediaStorage {

	private static final Logger log = LoggerFactory.getLogger(MediaStorage.class);
	private static final int NOT_FOUND = 404;

	private final S3Client s3;
	private final S3Presigner presigner;
	private final StorageProperties properties;

	public MediaStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
		this.s3 = s3;
		this.presigner = presigner;
		this.properties = properties;
	}

	public void put(String key, byte[] data, String contentType) {
		s3.putObject(b -> b.bucket(properties.bucket()).key(key).contentType(contentType),
				RequestBody.fromBytes(data));
	}

	/** S3 の中でコピーする（データは EC2 を通らない） */
	public void copy(String fromKey, String toKey) {
		s3.copyObject(b -> b.sourceBucket(properties.bucket()).sourceKey(fromKey)
				.destinationBucket(properties.bucket()).destinationKey(toKey));
	}

	/**
	 * 消す。失敗してもエラーにはしない（投稿の保存・削除はもう終わっているため）。ログに残す。
	 * ない キーを消しても S3 はエラーにしない。
	 */
	public void deleteQuietly(Collection<String> keys) {
		for (String key : keys) {
			try {
				s3.deleteObject(b -> b.bucket(properties.bucket()).key(key));
			} catch (RuntimeException e) {
				log.warn("S3 のファイルを消せませんでした: {}", key, e);
			}
		}
	}

	/** ファイルの大きさ（バイト）。ファイルがなければ空 */
	public OptionalLong size(String key) {
		try {
			return OptionalLong.of(s3.headObject(b -> b.bucket(properties.bucket()).key(key)).contentLength());
		} catch (NoSuchKeyException e) {
			return OptionalLong.empty();
		} catch (S3Exception e) {
			// HEAD の返事には本文がないので、ないファイルは「404 の S3Exception」になることがある
			if (e.statusCode() == NOT_FOUND) {
				return OptionalLong.empty();
			}
			throw e;
		}
	}

	/** パソコン（EC2）の一時ファイルに取り出す。file はまだないこと */
	public void download(String key, Path file) {
		s3.getObject(b -> b.bucket(properties.bucket()).key(key), ResponseTransformer.toFile(file));
	}

	/** 画像・動画を見るための期限付きの URL */
	public String viewUrl(String key) {
		return presigner.presignGetObject(b -> b.signatureDuration(properties.viewUrlTtl())
				.getObjectRequest(r -> r.bucket(properties.bucket()).key(key)))
				.url().toString();
	}

	/**
	 * 画面が S3 へ直接送る（PUT）ための期限付きの URL。
	 * 種類（Content-Type）と大きさ（Content-Length）を署名に入れるので、本物の S3 は違うものを断る
	 * （S3Mock は署名を確かめないので、開発中は断らない。送ったあとにサーバーでも確かめる）。
	 */
	public String uploadUrl(String key, String contentType, long size) {
		return presigner.presignPutObject(b -> b.signatureDuration(properties.uploadUrlTtl())
				.putObjectRequest(r -> r.bucket(properties.bucket()).key(key)
						.contentType(contentType).contentLength(size)))
				.url().toString();
	}

}
