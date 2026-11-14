package io.github.keiwebcreate.raisetechtimeline.follow.dto;

/**
 * フォロー・解除の返事。画面は、押したときに先に変えた表示を、この値で正しくそろえる。
 *
 * @param following フォローしているか
 * @param followerCount 相手のフォロワー数（ほかの人のフォローも含めた、今の数）
 */
public record FollowResponse(boolean following, long followerCount) {
}
