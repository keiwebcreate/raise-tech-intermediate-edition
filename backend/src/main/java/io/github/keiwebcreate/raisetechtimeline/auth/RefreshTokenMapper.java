package io.github.keiwebcreate.raisetechtimeline.auth;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * refresh_tokens テーブルの読み書き（MyBatis）。SQL は src/main/resources/mapper/RefreshTokenMapper.xml。
 */
@Mapper
public interface RefreshTokenMapper {

	void insert(@Param("userId") long userId, @Param("tokenHash") String tokenHash,
			@Param("expiresAt") OffsetDateTime expiresAt);

	Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

	/**
	 * まだ有効なら使えなくする。
	 *
	 * @return 使えなくした行の数。0 なら、ほかの通信がひと足先に使った（取り替えた）
	 */
	int revokeIfActive(@Param("id") long id, @Param("now") OffsetDateTime now);

	/** その人のまだ有効なトークンを、すべて使えなくする（使い回しを見つけたとき） */
	int revokeAllByUserId(@Param("userId") long userId, @Param("now") OffsetDateTime now);

	/** 期限の切れたトークンを消す（片付け） */
	int deleteExpired(@Param("now") OffsetDateTime now);

}
