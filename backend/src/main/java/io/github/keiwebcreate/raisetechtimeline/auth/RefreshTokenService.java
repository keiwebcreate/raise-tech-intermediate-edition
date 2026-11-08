package io.github.keiwebcreate.raisetechtimeline.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.common.error.RefreshTokenInvalidException;

/**
 * リフレッシュトークンの発行・取り替え・取り消し（docs/features/authentication.md 7）。
 *
 * - トークンは 32 バイトのランダムな文字列（JWT ではない）。DB には SHA-256 のハッシュだけを保存する
 * - 使うたびに新しいものに取り替える（ローテーション）。古いものは使えなくする
 * - 使えなくしたトークンがもう一度使われたら、盗まれたとみなして、その人のトークンをすべて使えなくする
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenMapper mapper;
	private final AuthProperties properties;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshTokenMapper mapper, AuthProperties properties, Clock clock) {
		this.mapper = mapper;
		this.properties = properties;
		this.clock = clock;
	}

	/** 新しいトークンを作って DB に保存し、Cookie に入れる元の文字列を返す */
	@Transactional
	public String issue(long userId) {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		mapper.insert(userId, hash(token), now().plus(properties.refreshTokenTtl()));
		return token;
	}

	/**
	 * トークンを確かめ、使えなくしてから、新しいトークンを発行する。
	 * 使い回しを見つけたときに「すべて使えなくする」を残すため、例外のときもロールバックしない。
	 *
	 * @return 新しいトークンと、その持ち主
	 * @throws RefreshTokenInvalidException ない・期限切れ・使えなくなっているとき
	 */
	@Transactional(noRollbackFor = RefreshTokenInvalidException.class)
	public Rotated rotate(@Nullable String token) {
		if (token == null || token.isBlank()) {
			throw new RefreshTokenInvalidException("リフレッシュトークンがありません");
		}
		RefreshToken current = mapper.findByTokenHash(hash(token))
				.orElseThrow(() -> new RefreshTokenInvalidException("知らないリフレッシュトークンです"));
		OffsetDateTime now = now();
		if (current.revokedAt() != null) {
			revokeAllBecauseOfReuse(current.userId(), now);
		}
		if (!current.expiresAt().isAfter(now)) {
			throw new RefreshTokenInvalidException("リフレッシュトークンの期限が切れています");
		}
		// 同時に2回使われたときは、ここで片方だけが成功する。負けた方は使い回しとみなす
		if (mapper.revokeIfActive(current.id(), now) == 0) {
			revokeAllBecauseOfReuse(current.userId(), now);
		}
		return new Rotated(current.userId(), issue(current.userId()));
	}

	/** ログアウト：そのトークンを使えなくする。ない・知らないトークンなら何もしない */
	@Transactional
	public void revoke(@Nullable String token) {
		if (token == null || token.isBlank()) {
			return;
		}
		Optional<RefreshToken> current = mapper.findByTokenHash(hash(token));
		current.ifPresent(t -> mapper.revokeIfActive(t.id(), now()));
	}

	/** 期限の切れたトークンを消す（RefreshTokenCleanupJob から1日1回） */
	@Transactional
	public int deleteExpired() {
		return mapper.deleteExpired(now());
	}

	private void revokeAllBecauseOfReuse(long userId, OffsetDateTime now) {
		int count = mapper.revokeAllByUserId(userId, now);
		// トークンそのものはログに出さない。ユーザーは ID で記録する
		log.warn("使用済みのリフレッシュトークンが使われたため、ユーザー {} のトークン {} 件を取り消しました", userId, count);
		throw new RefreshTokenInvalidException("使用済みのリフレッシュトークンです");
	}

	private OffsetDateTime now() {
		return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	/** SHA-256 のハッシュ（16進数64文字）。テストからも使う */
	static String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 は Java に必ず入っているので、ここには来ない
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 取り替えの結果。
	 *
	 * @param userId トークンの持ち主
	 * @param token 新しいリフレッシュトークン（Cookie に入れる）
	 */
	public record Rotated(long userId, String token) {
	}

}
