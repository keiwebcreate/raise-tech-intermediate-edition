package io.github.keiwebcreate.raisetechtimeline.media;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 画像・動画の保存先（application.yml の app.storage）。
 *
 * @param bucket S3 のバケット名
 * @param region リージョン（シドニー ap-southeast-2）
 * @param endpoint 接続先。空なら本物の S3。開発・テストでは S3Mock の住所（例：http://localhost:9090）
 * @param viewUrlTtl 画像・動画を見るための期限付き URL の期限
 * @param uploadUrlTtl 動画を S3 へ直接送るための期限付き URL の期限
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(
		String bucket,
		String region,
		@Nullable String endpoint,
		Duration viewUrlTtl,
		Duration uploadUrlTtl) {

	/** S3Mock につなぐか（接続先が書いてあるか） */
	public boolean usesMock() {
		return endpoint != null && !endpoint.isBlank();
	}

}
