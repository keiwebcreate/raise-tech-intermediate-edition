package io.github.keiwebcreate.raisetechtimeline;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;

import io.github.keiwebcreate.raisetechtimeline.support.SqlCounter;

// 機能ごとのパッケージ（auth など）のテストからも使うので public にする
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** テストで使うバケット（application-test.yml の app.storage.bucket と同じ） */
	public static final String BUCKET = "raisetechtimeline-test";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// 開発・本番と同じ PostgreSQL 18 でテストする
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

	@Bean
	S3MockContainer s3MockContainer() {
		// 開発（compose.yaml）と同じ版の S3Mock。起動したときにバケットを作る
		return new S3MockContainer("5.2.3").withInitialBuckets(BUCKET).withRegion("ap-southeast-2");
	}

	// S3Mock の住所（起動するたびにポートが変わる）を app.storage.endpoint に入れる
	@Bean
	DynamicPropertyRegistrar s3MockProperties(S3MockContainer s3Mock) {
		return registry -> registry.add("app.storage.endpoint", s3Mock::getHttpEndpoint);
	}

	// MyBatis が送った SQL の数を数える道具（N+1 問題のテストで使う）。
	// 別の設定に分けると、テストごとに Spring の準備と DB のコンテナが増えるので、ここに一緒に置く
	@Bean
	SqlCounter sqlCounter() {
		return new SqlCounter();
	}

}
