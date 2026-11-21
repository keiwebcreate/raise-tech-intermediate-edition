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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * コメント・返信へのいいねの API（docs/features/like.md 4.4・8章）。投稿へのいいね（LikeController）と同じ形。
 *
 * | メソッド・URL                          | 中身 |
 * |---|---|
 * | PUT    /api/comments/{id}/like          | いいねする（何回送っても同じ結果） |
 * | DELETE /api/comments/{id}/like          | いいねを取り消す |
 * | GET    /api/comments/{id}/likes?before= | いいねした人（新しい順に20件） |
 */
@Tag(name = "いいね")
@RestController
@RequestMapping("/api/comments/{commentId:\\d+}")
public class CommentLikeController {

	private final LikeService likeService;

	public CommentLikeController(LikeService likeService) {
		this.likeService = likeService;
	}

	@Operation(summary = "コメントにいいねする")
	@ApiResponse(responseCode = "200", description = "いいねした。もういいねしていても同じ返事")
	@ApiResponse(responseCode = "404", description = "コメントがない（COMMENT_NOT_FOUND）")
	@PutMapping("/like")
	LikeResponse like(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long commentId) {
		return likeService.likeComment(loginUser.id(), commentId);
	}

	@Operation(summary = "コメントのいいねを取り消す")
	@ApiResponse(responseCode = "200", description = "取り消した。いいねしていなくても同じ返事")
	@ApiResponse(responseCode = "404", description = "コメントがない（COMMENT_NOT_FOUND）")
	@DeleteMapping("/like")
	LikeResponse unlike(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long commentId) {
		return likeService.unlikeComment(loginUser.id(), commentId);
	}

	@Operation(summary = "コメントにいいねした人")
	@ApiResponse(responseCode = "200", description = "新しくいいねした順に20件。続きは before に nextBefore")
	@ApiResponse(responseCode = "400", description = "続きの目印（before・after）が数でない（VALIDATION_ERROR）")
	@ApiResponse(responseCode = "404", description = "コメントがない（COMMENT_NOT_FOUND）")
	@GetMapping("/likes")
	LikersResponse likers(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long commentId,
			@RequestParam(required = false) @Nullable Long before) {
		return likeService.commentLikers(loginUser.id(), commentId, before);
	}

}
