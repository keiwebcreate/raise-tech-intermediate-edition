package io.github.keiwebcreate.raisetechtimeline.comment;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.raisetechtimeline.auth.LoginUser;
import io.github.keiwebcreate.raisetechtimeline.comment.dto.CommentRequest;
import io.github.keiwebcreate.raisetechtimeline.comment.dto.CommentResponse;
import io.github.keiwebcreate.raisetechtimeline.comment.dto.CommentsResponse;
import io.github.keiwebcreate.raisetechtimeline.comment.dto.RepliesResponse;
import jakarta.validation.Valid;

/**
 * コメント・返信の API（docs/features/comment.md 8章）。すべてログインが必要。
 *
 * | メソッド・URL                          | 中身 |
 * |---|---|
 * | GET    /api/posts/{id}/comments?after= | 投稿への直接のコメント（古い順に50件） |
 * | POST   /api/posts/{id}/comments        | コメント・返信する（返信は parentCommentId を付ける） |
 * | GET    /api/comments/{id}/replies      | 一番上のコメントの下の返信すべて（古い順） |
 * | DELETE /api/comments/{id}              | 削除（本人だけ。下の返信も消える） |
 */
@RestController
@RequestMapping("/api")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	// {postId:\d+}：数字だけ。数字以外は 404 にする
	@GetMapping("/posts/{postId:\\d+}/comments")
	CommentsResponse comments(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
			@RequestParam(required = false) @Nullable Long after) {
		return commentService.comments(loginUser.id(), postId, after);
	}

	@PostMapping("/posts/{postId:\\d+}/comments")
	ResponseEntity<CommentResponse> create(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
			@Valid @RequestBody CommentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(
				commentService.create(loginUser.id(), postId, request.content(), request.parentCommentId()));
	}

	@GetMapping("/comments/{id:\\d+}/replies")
	RepliesResponse replies(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		return commentService.replies(loginUser.id(), id);
	}

	@DeleteMapping("/comments/{id:\\d+}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		commentService.delete(loginUser.id(), id);
		return ResponseEntity.noContent().build();
	}

}
