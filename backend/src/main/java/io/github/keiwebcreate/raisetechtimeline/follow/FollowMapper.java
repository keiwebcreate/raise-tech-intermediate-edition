package io.github.keiwebcreate.raisetechtimeline.follow;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * follows テーブルの読み書き（MyBatis）。SQL は src/main/resources/mapper/FollowMapper.xml に書く。
 */
@Mapper
public interface FollowMapper {

	/** ユーザー名（小文字にそろえてから渡す）の人の ID */
	Optional<Long> findUserId(@Param("username") String username);

	/** フォローを足す。もうフォローしていたら何もしない（足した行の数を返す。0 か 1） */
	int insert(@Param("followerId") long followerId, @Param("followingId") long followingId);

	/** フォローを消す。フォローしていなければ何もしない */
	int delete(@Param("followerId") long followerId, @Param("followingId") long followingId);

	/** その人のフォロワーの数 */
	long countFollowers(@Param("userId") long userId);

	/**
	 * その人がフォローしている人。新しくフォローした順。
	 *
	 * @param viewerId 見ている人（following・followedBy を、この人から見た値にする）
	 * @param beforeId このフォロー（follows.id）より前のものだけ（続きを読むとき）。null なら最新から
	 */
	List<FollowUser> findFollowing(@Param("userId") long userId, @Param("viewerId") long viewerId,
			@Param("beforeId") @Nullable Long beforeId, @Param("limit") int limit);

	/** その人をフォローしている人。新しくフォローされた順。引数は findFollowing と同じ */
	List<FollowUser> findFollowers(@Param("userId") long userId, @Param("viewerId") long viewerId,
			@Param("beforeId") @Nullable Long beforeId, @Param("limit") int limit);

}
