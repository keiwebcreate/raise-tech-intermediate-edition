package io.github.keiwebcreate.raisetechtimeline.user;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

/**
 * users テーブルの1行（docs/database-design.md 3.1）。
 * MyBatis が SQL の結果をこの record に入れる。API の返事には使わず、DTO（UserResponse など）に詰め替える。
 */
public record User(
		long id,
		String email,
		String passwordHash,
		String username,
		String displayName,
		@Nullable String bio,
		@Nullable String iconImageKey,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
}
