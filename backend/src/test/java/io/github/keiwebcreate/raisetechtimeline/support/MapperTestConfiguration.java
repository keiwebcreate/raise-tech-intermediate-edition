package io.github.keiwebcreate.raisetechtimeline.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Mapper のテスト（@MybatisTest）用の設定。PostgreSQL 18 だけを動かす（S3 などは使わない）。
 * テーブルは Flyway で本番と同じものを作る。
 */
@TestConfiguration(proxyBeanMethods = false)
public class MapperTestConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

}
