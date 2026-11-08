package io.github.keiwebcreate.raisetechtimeline.user;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

}
