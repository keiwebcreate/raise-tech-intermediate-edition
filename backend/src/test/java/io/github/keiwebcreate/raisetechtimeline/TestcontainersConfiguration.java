package io.github.keiwebcreate.raisetechtimeline;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// 機能ごとのパッケージ（auth など）のテストからも使うので public にする
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// 開発・本番と同じ PostgreSQL 18 でテストする
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

}
