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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "コメント", description = "コメント・返信（docs/features/comment.md 8章）")
@RestController
@RequestMapping("/api")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	// {postId:\d+}：数字だけ。数字以外は 404 にする
	@Operation(summary = "投稿へのコメント")
	@ApiResponse(responseCode = "200", description = "直接のコメントを古い順に50件（返信は入らない）。続きは after に nextAfter")
	@ApiResponse(responseCode = "400", description = "続きの目印（before・after）が数でない（VALIDATION_ERROR）")
	@ApiResponse(responseCode = "404", description = "投稿がない（POST_NOT_FOUND）")
	@GetMapping("/posts/{postId:\\d+}/comments")
	CommentsResponse comments(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
			@RequestParam(required = false) @Nullable Long after) {
		return commentService.comments(loginUser.id(), postId, after);
	}

	@Operation(summary = "コメント・返信する", description = "本文は280文字まで。返信なら parentCommentId に返信先のコメントの ID")
	@ApiResponse(responseCode = "201", description = "コメントした")
	@ApiResponse(responseCode = "400", description = "入力の誤り（VALIDATION_ERROR。errors[].field は content）")
	@ApiResponse(responseCode = "404", description = "投稿がない（POST_NOT_FOUND）")
	@ApiResponse(responseCode = "404", description = "返信先のコメントがない・別の投稿のコメント（COMMENT_NOT_FOUND）")
	@PostMapping("/posts/{postId:\\d+}/comments")
	ResponseEntity<CommentResponse> create(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long postId,
			@Valid @RequestBody CommentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(
				commentService.create(loginUser.id(), postId, request.content(), request.parentCommentId()));
	}

	@Operation(summary = "コメントの下の返信")
	@ApiResponse(responseCode = "200", description = "下に並ぶ返信すべて（返信の返信も。古い順）")
	@ApiResponse(responseCode = "404", description = "コメントがない（COMMENT_NOT_FOUND）")
	@GetMapping("/comments/{id:\\d+}/replies")
	RepliesResponse replies(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		return commentService.replies(loginUser.id(), id);
	}

	@Operation(summary = "コメントを削除する", description = "本人だけ")
	@ApiResponse(responseCode = "204", description = "削除した（下の返信・いいねも消える）")
	@ApiResponse(responseCode = "404", description = "コメントがない・他人のコメント（COMMENT_NOT_FOUND）")
	@DeleteMapping("/comments/{id:\\d+}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		commentService.delete(loginUser.id(), id);
		return ResponseEntity.noContent().build();
	}

}
