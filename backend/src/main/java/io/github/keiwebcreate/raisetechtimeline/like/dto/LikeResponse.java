package io.github.keiwebcreate.raisetechtimeline.like.dto;

/**
 * いいね・取り消しの返事。画面は、押したときに先に変えた表示を、この値で正しくそろえる。
 *
 * @param liked いいねしているか
 * @param likeCount いいね数（ほかの人のいいねも含めた、今の数）
 */
public record LikeResponse(boolean liked, long likeCount) {
}
