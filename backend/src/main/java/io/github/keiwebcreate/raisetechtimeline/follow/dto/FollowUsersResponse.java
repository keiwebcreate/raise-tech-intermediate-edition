package io.github.keiwebcreate.raisetechtimeline.follow.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * フォロー中・フォロワーの一覧の返事（S-07。docs/features/profile.md 4.5）。
 *
 * @param users 新しくフォローした順（20件まで）
 * @param nextBefore 続きを読むときに before に入れる値。続きがなければ null
 */
public record FollowUsersResponse(List<User> users, @Nullable Long nextBefore) {

	/**
	 * 一覧の1人。メールアドレスは他人に見せないので入れない。
	 *
	 * @param bio 自己紹介。なければ null
	 * @param iconUrl アイコンを見るための期限付きの URL。未設定なら null
	 * @param following 見ている人が、この人をフォローしているか
	 * @param followedBy この人が、見ている人をフォローしているか（「フォローされています」）
	 */
	public record User(long id, String username, String displayName, @Nullable String bio,
			@Nullable String iconUrl, boolean following, boolean followedBy) {
	}

}
