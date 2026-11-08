package io.github.keiwebcreate.raisetechtimeline.media;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 とやりとりする道具（AWS SDK for Java 2）を作る。
 *
 * - 本番：本物の S3。鍵はコードに書かず、EC2 に付けたロール（IAM ロール）から SDK が自動で読む
 * - 開発・テスト：S3Mock。鍵を確かめないので、仮の文字を渡す。住所は http://localhost:9090/バケット名/キー の形（path-style）
 */
@Configuration
public class S3Config {

	// S3Mock は鍵を確かめない。本物の鍵ではないので、コードに書いてよい
	private static final String MOCK_KEY = "s3mock";

	@Bean
	S3Client s3Client(StorageProperties properties) {
		var builder = S3Client.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(credentials(properties))
				// 送るデータの検査用の値（チェックサム）は、S3 が求めるときだけ付ける
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
		if (properties.usesMock()) {
			builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
		}
		return builder.build();
	}

	@Bean
	S3Presigner s3Presigner(StorageProperties properties) {
		var builder = S3Presigner.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(credentials(properties));
		if (properties.usesMock()) {
			builder.endpointOverride(URI.create(properties.endpoint()))
					.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
		}
		return builder.build();
	}

	private static AwsCredentialsProvider credentials(StorageProperties properties) {
		if (properties.usesMock()) {
			return StaticCredentialsProvider.create(AwsBasicCredentials.create(MOCK_KEY, MOCK_KEY));
		}
		return DefaultCredentialsProvider.builder().build();
	}

}
