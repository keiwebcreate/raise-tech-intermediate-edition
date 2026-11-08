package io.github.keiwebcreate.raisetechtimeline.like.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * いいねした人の一覧の返事（M-02）。
 *
 * @param users 新しくいいねした順（20件まで）
 * @param nextBefore 続きを読むときに before に入れる値。続きがなければ null
 */
public record LikersResponse(List<User> users, @Nullable Long nextBefore) {

	/** いいねした人。メールアドレスは他人に見せないので入れない。自己紹介・フォローの状態は、その機能のときに足す */
	public record User(long id, String username, String displayName) {
	}

}
