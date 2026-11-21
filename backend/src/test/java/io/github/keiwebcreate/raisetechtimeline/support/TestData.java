package io.github.keiwebcreate.raisetechtimeline.support;

import java.time.OffsetDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Mapper のテストで、DB に直接データを入れる道具。
 * Mapper のテストは1つのテストを1つのトランザクションで動かし、終わったら取り消す（@MybatisTest）。
 * そのため、テストの中の now() はすべて同じ時刻になる。並び順を確かめるときは、日時をはっきり決めて入れる。
 */
public final class TestData {

	private final JdbcTemplate jdbc;

	public TestData(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	public JdbcTemplate jdbc() {
		return jdbc;
	}

	/** ユーザーを足す。ユーザー名は重ならないようにランダム。表示名は決められる */
	public long user(String displayName) {
		String username = "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		return user(username, displayName);
	}

	public long user(String username, String displayName) {
		return id("INSERT INTO users (email, password_hash, username, display_name) VALUES (?, 'not-used', ?, ?)"
				+ " RETURNING id", username + "@example.com", username, displayName);
	}

	public long post(long userId, OffsetDateTime createdAt) {
		return id("INSERT INTO posts (user_id, content, created_at) VALUES (?, '本文', ?) RETURNING id", userId,
				createdAt);
	}

	public long comment(long postId, long userId, OffsetDateTime createdAt) {
		return id("INSERT INTO comments (post_id, user_id, content, created_at) VALUES (?, ?, 'コメント', ?) RETURNING id",
				postId, userId, createdAt);
	}

	public void follow(long follower, long following) {
		jdbc.update("INSERT INTO follows (follower_id, following_id) VALUES (?, ?)", follower, following);
	}

	public void like(long postId, long userId) {
		jdbc.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", postId, userId);
	}

	public long count(String sql, Object... args) {
		Long count = jdbc.queryForObject(sql, Long.class, args);
		return count == null ? 0 : count;
	}

	private long id(String sql, Object... args) {
		Long id = jdbc.queryForObject(sql, Long.class, args);
		if (id == null) {
			throw new IllegalStateException("ID が返りませんでした: " + sql);
		}
		return id;
	}

}
