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
 * 投稿へのいいねの API（docs/features/like.md 8章）。すべてログインが必要。
 *
 * | メソッド・URL                       | 中身 |
 * |---|---|
 * | PUT    /api/posts/{id}/like          | いいねする（何回送っても同じ結果） |
 * | DELETE /api/posts/{id}/like          | いいねを取り消す |
 * | GET    /api/posts/{id}/likes?before= | いいねした人（新しい順に20件） |
 */
@Tag(name = "いいね", description = "投稿・コメントへのいいね（docs/features/like.md 8章）")
@RestController
@RequestMapping("/api/posts/{postId:\\d+}")
public class LikeController {

	private final LikeService likeService;

	public LikeController(LikeService likeService) {
		this.likeService = likeService;
	}

	// PUT・DELETE：「いいねしている状態にする／していない状態にする」。何回送っても結果が同じなので、連打や送り直しで数がずれない
	@Operation(summary = "投稿にいいねする")
	@ApiResponse(responseCode = "200", description = "いいねした。もういいねしていても同じ返事")
	@ApiResponse(responseCode = "404", description = "投稿がない（POST_NOT_FOUND）")
	@PutMapping("/like")
	LikeResponse like(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId) {
		return likeService.like(loginUser.id(), postId);
	}

	@Operation(summary = "投稿のいいねを取り消す")
	@ApiResponse(responseCode = "200", description = "取り消した。いいねしていなくても同じ返事")
	@ApiResponse(responseCode = "404", description = "投稿がない（POST_NOT_FOUND）")
	@DeleteMapping("/like")
	LikeResponse unlike(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId) {
		return likeService.unlike(loginUser.id(), postId);
	}

	@Operation(summary = "投稿にいいねした人")
	@ApiResponse(responseCode = "200", description = "新しくいいねした順に20件。続きは before に nextBefore")
	@ApiResponse(responseCode = "400", description = "続きの目印（before・after）が数でない（VALIDATION_ERROR）")
	@ApiResponse(responseCode = "404", description = "投稿がない（POST_NOT_FOUND）")
	@GetMapping("/likes")
	LikersResponse likers(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
			@RequestParam(required = false) @Nullable Long before) {
		return likeService.likers(loginUser.id(), postId, before);
	}

}
