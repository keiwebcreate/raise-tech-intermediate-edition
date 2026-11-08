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
 * 投稿へのいいねの API（docs/features/like.md 8章）。すべてログインが必要。
 *
 * | メソッド・URL                       | 中身 |
 * |---|---|
 * | PUT    /api/posts/{id}/like          | いいねする（何回送っても同じ結果） |
 * | DELETE /api/posts/{id}/like          | いいねを取り消す |
 * | GET    /api/posts/{id}/likes?before= | いいねした人（新しい順に20件） |
 */
@RestController
@RequestMapping("/api/posts/{postId:\\d+}")
public class LikeController {

	private final LikeService likeService;

	public LikeController(LikeService likeService) {
		this.likeService = likeService;
	}

	// PUT・DELETE：「いいねしている状態にする／していない状態にする」。何回送っても結果が同じなので、連打や送り直しで数がずれない
	@PutMapping("/like")
	LikeResponse like(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId) {
		return likeService.like(loginUser.id(), postId);
	}

	@DeleteMapping("/like")
	LikeResponse unlike(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId) {
		return likeService.unlike(loginUser.id(), postId);
	}

	@GetMapping("/likes")
	LikersResponse likers(@PathVariable long postId, @RequestParam(required = false) @Nullable Long before) {
		return likeService.likers(postId, before);
	}

}
