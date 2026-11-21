package io.github.keiwebcreate.raisetechtimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import io.github.keiwebcreate.raisetechtimeline.support.MapperTestConfiguration;
import io.github.keiwebcreate.raisetechtimeline.support.TestData;

/**
 * RefreshTokenMapper（refresh_tokens の SQL）のテスト（docs/features/authentication.md 7.4）。
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MapperTestConfiguration.class)
class RefreshTokenMapperTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-21T03:00:00Z");

	@Autowired
	private RefreshTokenMapper mapper;

	@Autowired
	private DataSource dataSource;

	private TestData data;

	private long me;

	@BeforeEach
	void setUp() {
		data = new TestData(dataSource);
		me = data.user("自分");
	}

	@Test
	void ハッシュで探せる() {
		mapper.insert(me, "hash-a", NOW.plusDays(7));

		RefreshToken token = mapper.findByTokenHash("hash-a").orElseThrow();
		assertThat(token.userId()).isEqualTo(me);
		assertThat(token.expiresAt()).isEqualTo(NOW.plusDays(7));
		assertThat(token.revokedAt()).isNull();
		assertThat(mapper.findByTokenHash("unknown")).isEmpty();
	}

	@Test
	void 使えなくできるのは1回だけ_2回目は0行で_ほかの通信が先に使ったと分かる() {
		mapper.insert(me, "hash-a", NOW.plusDays(7));
		long id = mapper.findByTokenHash("hash-a").orElseThrow().id();

		assertThat(mapper.revokeIfActive(id, NOW)).isEqualTo(1);
		assertThat(mapper.revokeIfActive(id, NOW.plusSeconds(1))).isZero();
		assertThat(mapper.findByTokenHash("hash-a").orElseThrow().revokedAt()).isEqualTo(NOW);
	}

	@Test
	void 使い回しを見つけたら_その人のまだ有効なトークンだけを_すべて使えなくする() {
		long other = data.user("ほかの人");
		mapper.insert(me, "mine-1", NOW.plusDays(7));
		mapper.insert(me, "mine-2", NOW.plusDays(7));
		mapper.insert(other, "others", NOW.plusDays(7));
		mapper.revokeIfActive(mapper.findByTokenHash("mine-1").orElseThrow().id(), NOW.minusHours(1));

		assertThat(mapper.revokeAllByUserId(me, NOW)).as("まだ有効だった1つ").isEqualTo(1);
		assertThat(mapper.findByTokenHash("mine-1").orElseThrow().revokedAt()).as("前に使えなくした日時のまま")
				.isEqualTo(NOW.minusHours(1));
		assertThat(mapper.findByTokenHash("mine-2").orElseThrow().revokedAt()).isEqualTo(NOW);
		assertThat(mapper.findByTokenHash("others").orElseThrow().revokedAt()).as("ほかの人のはそのまま").isNull();
	}

	@Test
	void 片付けでは期限が切れたものだけを消し_使えなくしただけのものは期限まで残す() {
		mapper.insert(me, "expired", NOW.minusSeconds(1));
		mapper.insert(me, "revoked", NOW.plusDays(1));
		mapper.insert(me, "active", NOW.plusDays(1));
		mapper.revokeIfActive(mapper.findByTokenHash("revoked").orElseThrow().id(), NOW.minusHours(1));

		assertThat(mapper.deleteExpired(NOW)).isEqualTo(1);
		assertThat(mapper.findByTokenHash("expired")).isEmpty();
		assertThat(mapper.findByTokenHash("revoked")).as("使い回しを見つけるため、期限まで残す").isPresent();
		assertThat(mapper.findByTokenHash("active")).isPresent();
	}

}
