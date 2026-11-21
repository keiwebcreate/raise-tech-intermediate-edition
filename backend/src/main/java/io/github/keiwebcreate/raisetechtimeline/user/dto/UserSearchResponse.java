package io.github.keiwebcreate.raisetechtimeline.user.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.keiwebcreate.raisetechtimeline.follow.dto.FollowUsersResponse;

/**
 * ユーザー検索の返事（S-08。docs/features/user-search.md 8章）。
 * 1人の形は、フォロー中・フォロワーの一覧と同じ（画面で同じ部品 UserListItem を使う）。
 *
 * @param users 一致の順位・フォロワーの多い順（20件まで）
 * @param nextOffset 続きを読むときに offset に入れる値。続きがなければ null
 */
public record UserSearchResponse(List<FollowUsersResponse.User> users, @Nullable Integer nextOffset) {
}
