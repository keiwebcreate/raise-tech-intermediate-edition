package io.github.keiwebcreate.raisetechtimeline.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.keiwebcreate.raisetechtimeline.common.error.UserNotFoundException;
import io.github.keiwebcreate.raisetechtimeline.media.MediaService;
import io.github.keiwebcreate.raisetechtimeline.post.PostService;
import io.github.keiwebcreate.raisetechtimeline.post.dto.TimelineResponse;
import io.github.keiwebcreate.raisetechtimeline.user.dto.ProfileResponse;
import io.github.keiwebcreate.raisetechtimeline.user.dto.ProfileUpdateRequest;

/**
 * プロフィールの表示と編集（docs/features/profile.md 3章）。
 * 編集は、ログインしている本人のプロフィールだけ（URL にユーザーを指定させないので、他人のものは変えられない）。
 */
@Service
public class UserService {

	private final UserMapper userMapper;
	private final PostService postService;
	private final MediaService mediaService;

	public UserService(UserMapper userMapper, PostService postService, MediaService mediaService) {
		this.userMapper = userMapper;
		this.postService = postService;
		this.mediaService = mediaService;
	}

	/**
	 * プロフィール。
	 *
	 * @throws UserNotFoundException そのユーザー名の人がいない
	 */
	@Transactional(readOnly = true)
	public ProfileResponse profile(long viewerId, String username) {
		return toResponse(findByUsername(username), viewerId);
	}

	/**
	 * その人の投稿の一覧（新しい順に20件）。
	 *
	 * @param before 続きを読むとき、前の返事の最後の投稿の ID
	 * @throws UserNotFoundException そのユーザー名の人がいない
	 */
	@Transactional(readOnly = true)
	public TimelineResponse posts(long viewerId, String username, @Nullable Long before) {
		return postService.postsByAuthor(viewerId, findByUsername(username).id(), before);
	}

	/**
	 * 本人のプロフィールを変える。アイコンを差し替え・外したら、前の画像はコミットのあとに S3 から消す（profile.md 3.2）。
	 *
	 * @throws io.github.keiwebcreate.raisetechtimeline.common.error.FieldValidationException 文字数・アイコンのキーの誤り
	 */
	@Transactional
	public ProfileResponse update(long userId, ProfileUpdateRequest request) {
		String displayName = ProfileText.displayName(request.displayName());
		String bio = ProfileText.bio(request.bio());
		Profile current = userMapper.findProfileById(userId).orElseThrow(UserNotFoundException::new);
		String currentKey = current.iconImageKey();
		String requestedKey = request.iconKey();

		String newKey = currentKey;
		List<String> deleteAfterCommit = new ArrayList<>();
		List<String> deleteAfterRollback = new ArrayList<>();
		if (requestedKey == null) {
			// 外す
			newKey = null;
		} else if (!requestedKey.equals(currentKey)) {
			// 差し替える：一時置き場（本人のもの）から正式な場所へコピー。DB の保存が失敗したらコピーを消す
			mediaService.deleteAfterRollback(deleteAfterRollback);
			newKey = mediaService.copyIcon(userId, requestedKey);
			deleteAfterRollback.add(newKey);
			deleteAfterCommit.add(requestedKey);
		}
		if (currentKey != null && !Objects.equals(currentKey, newKey) && mediaService.isIconKey(currentKey)) {
			deleteAfterCommit.add(currentKey);
		}

		userMapper.updateProfile(userId, displayName, bio, newKey);
		mediaService.deleteAfterCommit(deleteAfterCommit);
		return toResponse(userMapper.findProfileById(userId).orElseThrow(UserNotFoundException::new), userId);
	}

	private Profile findByUsername(String username) {
		// ユーザー名は小文字で保存している（Taro と taro を同じ人とみなす）
		return userMapper.findProfileByUsername(username.toLowerCase(Locale.ROOT))
				.orElseThrow(UserNotFoundException::new);
	}

	private ProfileResponse toResponse(Profile profile, long viewerId) {
		return new ProfileResponse(profile.id(), profile.username(), profile.displayName(), profile.bio(),
				mediaService.iconUrl(profile.iconImageKey()), profile.iconImageKey(), profile.createdAt(),
				profile.followingCount(), profile.followerCount(), profile.id() == viewerId);
	}

}
