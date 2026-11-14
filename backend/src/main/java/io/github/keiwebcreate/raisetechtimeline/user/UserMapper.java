package io.github.keiwebcreate.raisetechtimeline.user;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * users テーブルの読み書き（MyBatis）。SQL は src/main/resources/mapper/UserMapper.xml に書く。
 */
@Mapper
public interface UserMapper {

	Optional<User> findById(@Param("id") long id);

	/** メールアドレスは小文字にそろえてから渡す */
	Optional<User> findByEmail(@Param("email") String email);

	boolean existsByEmail(@Param("email") String email);

	/** ユーザー名は小文字にそろえてから渡す */
	boolean existsByUsername(@Param("username") String username);

	/** 1行足して、できたユーザーの ID を返す */
	long insert(@Param("email") String email, @Param("passwordHash") String passwordHash,
			@Param("username") String username, @Param("displayName") String displayName);

	/** プロフィール（フォロー数・フォロワー数を含む）。ユーザー名は小文字にそろえてから渡す */
	Optional<Profile> findProfileByUsername(@Param("username") String username);

	Optional<Profile> findProfileById(@Param("id") long id);

	/** 表示名・自己紹介・アイコンを変える。変えた行の数を返す */
	int updateProfile(@Param("id") long id, @Param("displayName") String displayName,
			@Param("bio") @Nullable String bio, @Param("iconImageKey") @Nullable String iconImageKey);

}
