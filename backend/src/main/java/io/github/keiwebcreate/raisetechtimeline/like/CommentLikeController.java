package io.github.keiwebcreate.raisetechtimeline.like;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.raisetechtimeline.auth.LoginUser;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikeResponse;
import io.github.keiwebcreate.raisetechtimeline.like.dto.LikersResponse;

/**
 * コメント・返信へのいいねの API（docs/features/like.md 4.4・8章）。投稿へのいいね（LikeController）と同じ形。
 *
 * | メソッド・URL                          | 中身 |
 * |---|---|
 * | PUT    /api/comments/{id}/like          | いいねする（何回送っても同じ結果） |
 * | DELETE /api/comments/{id}/like          | いいねを取り消す |
 * | GET    /api/comments/{id}/likes?before= | いいねした人（新しい順に20件） |
 */
@RestController
@RequestMapping("/api/comments/{commentId:\\d+}")
public class CommentLikeController {

	private final LikeService likeService;

	public CommentLikeController(LikeService likeService) {
		this.likeService = likeService;
	}

	@PutMapping("/like")
	LikeResponse like(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long commentId) {
		return likeService.likeComment(loginUser.id(), commentId);
	}

	@DeleteMapping("/like")
	LikeResponse unlike(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long commentId) {
		return likeService.unlikeComment(loginUser.id(), commentId);
	}

	@GetMapping("/likes")
	LikersResponse likers(@PathVariable long commentId, @RequestParam(required = false) @Nullable Long before) {
		return likeService.commentLikers(commentId, before);
	}

}
