package io.github.keiwebcreate.raisetechtimeline.auth;

import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

/**
 * refresh_tokens テーブルの1行（トークンそのものではなく、ハッシュで探した結果）。
 *
 * @param revokedAt 使えなくした日時。null なら有効
 */
public record RefreshToken(long id, long userId, OffsetDateTime expiresAt, @Nullable OffsetDateTime revokedAt) {
}
