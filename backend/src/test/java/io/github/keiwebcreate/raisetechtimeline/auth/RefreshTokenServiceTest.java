package io.github.keiwebcreate.raisetechtimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.keiwebcreate.raisetechtimeline.common.error.RefreshTokenInvalidException;
import io.github.keiwebcreate.raisetechtimeline.support.MutableClock;

/**
 * RefreshTokenService の単体テスト（docs/features/authentication.md 7.1・7.4）。
 * DB（RefreshTokenMapper）はにせもの、時計は MutableClock で決める。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");

	@Mock
	private RefreshTokenMapper mapper;

	private RefreshTokenService service;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties("not-used", Duration.ofMinutes(15), Duration.ofDays(7), true, 10,
				Duration.ofMinutes(15));
		service = new RefreshTokenService(mapper, properties, new MutableClock(NOW));
	}

	// ===== 作る（7.1） =====

	@Test
	void 作ったトークンは_DBにはSHA256のハッシュだけを_期限7日で保存する() {
		String token = service.issue(10L);

		ArgumentCaptor<String> savedHash = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<OffsetDateTime> expiresAt = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(mapper).insert(eq(10L), savedHash.capture(), expiresAt.capture());
		assertThat(savedHash.getValue()).as("トークンそのものは保存しない").isNotEqualTo(token)
				.isEqualTo(RefreshTokenService.hash(token));
		assertThat(expiresAt.getValue().toInstant()).isEqualTo(NOW.plus(Duration.ofDays(7)));
	}

	@Test
	void トークンは32バイトのランダムな文字列で_作るたびに違う() {
		String first = service.issue(10L);
		String second = service.issue(10L);

		assertThat(first).isNotEqualTo(second);
		// 32バイトは、Base64 で43文字以上・16進数で64文字
		assertThat(first.length()).isGreaterThanOrEqualTo(43);
	}

	@Test
	void ハッシュは同じトークンなら同じで_SHA256の長さ() {
		assertThat(RefreshTokenService.hash("abc")).isEqualTo(RefreshTokenService.hash("abc"))
				.isNotEqualTo(RefreshTokenService.hash("abd"));
		// SHA-256 は32バイト（16進数で64文字・Base64 で43〜44文字）
		assertThat(RefreshTokenService.hash("abc").length()).isBetween(43, 64);
	}

	// ===== 取り替える（7.3・7.4） =====

	@Test
	void 使えるトークンは_使えなくしてから_新しいトークンに取り替える() {
		when(mapper.findByTokenHash(RefreshTokenService.hash("old")))
				.thenReturn(Optional.of(token(1L, 10L, NOW.plus(Duration.ofDays(1)), null)));
		when(mapper.revokeIfActive(eq(1L), any())).thenReturn(1);

		RefreshTokenService.Rotated rotated = service.rotate("old");

		assertThat(rotated.userId()).isEqualTo(10L);
		assertThat(rotated.token()).isNotEqualTo("old");
		verify(mapper).insert(eq(10L), eq(RefreshTokenService.hash(rotated.token())), any());
		verify(mapper, never()).revokeAllByUserId(anyLong(), any());
	}

	@Test
	void トークンがない_知らないトークンは_REFRESH_INVALID() {
		assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(RefreshTokenInvalidException.class);
		verifyNoInteractions(mapper);

		when(mapper.findByTokenHash(anyString())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(RefreshTokenInvalidException.class);
		verify(mapper, never()).insert(anyLong(), anyString(), any());
	}

	@Test
	void 期限切れのトークンは_REFRESH_INVALIDで_新しいトークンを作らない() {
		when(mapper.findByTokenHash(RefreshTokenService.hash("old")))
				.thenReturn(Optional.of(token(1L, 10L, NOW.minusSeconds(1), null)));

		assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(RefreshTokenInvalidException.class);
		verify(mapper, never()).insert(anyLong(), anyString(), any());
	}

	@Test
	void 使用済みのトークンがもう一度使われたら_盗まれたとみなし_その人のトークンをすべて取り消す() {
		when(mapper.findByTokenHash(RefreshTokenService.hash("old")))
				.thenReturn(Optional.of(token(1L, 10L, NOW.plus(Duration.ofDays(1)), NOW.minusSeconds(60))));

		assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(RefreshTokenInvalidException.class);
		verify(mapper).revokeAllByUserId(eq(10L), any());
		verify(mapper, never()).insert(anyLong(), anyString(), any());
	}

	@Test
	void 同じトークンで同時に2回取り替えて_こちらが負けたら_使い回しとして扱う() {
		when(mapper.findByTokenHash(RefreshTokenService.hash("old")))
				.thenReturn(Optional.of(token(1L, 10L, NOW.plus(Duration.ofDays(1)), null)));
		// もう片方が先に使えなくした（更新できた行が0）
		when(mapper.revokeIfActive(eq(1L), any())).thenReturn(0);

		assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(RefreshTokenInvalidException.class);
		verify(mapper).revokeAllByUserId(eq(10L), any());
		verify(mapper, never()).insert(anyLong(), anyString(), any());
	}

	// ===== ログアウト・片付け（4.3・7.4） =====

	@Test
	void ログアウトは_そのトークンを使えなくする() {
		when(mapper.findByTokenHash(RefreshTokenService.hash("current")))
				.thenReturn(Optional.of(token(1L, 10L, NOW.plus(Duration.ofDays(1)), null)));

		service.revoke("current");

		verify(mapper).revokeIfActive(eq(1L), any());
	}

	@Test
	void ログアウトは_トークンがない_知らないトークンでもエラーにしない() {
		service.revoke(null);
		verifyNoInteractions(mapper);

		when(mapper.findByTokenHash(anyString())).thenReturn(Optional.empty());
		service.revoke("unknown");
		verify(mapper, never()).revokeIfActive(anyLong(), any());
	}

	@Test
	void 片付けは_今より前に期限が切れたものを消し_消した数を返す() {
		when(mapper.deleteExpired(any())).thenReturn(3);

		assertThat(service.deleteExpired()).isEqualTo(3);
		ArgumentCaptor<OffsetDateTime> now = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(mapper).deleteExpired(now.capture());
		assertThat(now.getValue().toInstant()).isEqualTo(NOW);
	}

	private static RefreshToken token(long id, long userId, Instant expiresAt, Instant revokedAt) {
		return new RefreshToken(id, userId, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
				revokedAt == null ? null : OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC));
	}

}
