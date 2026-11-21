package io.github.keiwebcreate.raisetechtimeline.post;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.raisetechtimeline.auth.LoginUser;
import io.github.keiwebcreate.raisetechtimeline.post.dto.NewPostCountResponse;
import io.github.keiwebcreate.raisetechtimeline.post.dto.PostRequest;
import io.github.keiwebcreate.raisetechtimeline.post.dto.PostResponse;
import io.github.keiwebcreate.raisetechtimeline.post.dto.TimelineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 投稿とタイムラインの API（docs/features/post.md・timeline.md）。すべてログインが必要。
 *
 * | メソッド・URL                       | 中身 |
 * |---|---|
 * | GET    /api/posts?tab=&before=&after= | タイムライン（20件ずつ） |
 * | GET    /api/posts/new-count?tab=&after= | 新しい投稿の数（画面が60秒ごとに呼ぶ） |
 * | POST   /api/posts                  | 投稿する（画像・動画は先に /api/media で送っておき、キーを付ける） |
 * | GET    /api/posts/{id}             | 投稿の詳細 |
 * | PUT    /api/posts/{id}             | 編集（本人だけ。画像・動画を外す・足すこともできる） |
 * | DELETE /api/posts/{id}             | 削除（本人だけ。画像・動画は S3 からも消す） |
 */
@Tag(name = "投稿・タイムライン", description = "投稿とタイムライン（docs/features/post.md・timeline.md）")
@RestController
@RequestMapping("/api/posts")
public class PostController {

	private final PostService postService;

	public PostController(PostService postService) {
		this.postService = postService;
	}

	@Operation(summary = "タイムライン",
			description = "tab：all（すべて）・following（自分とフォロー中の人）。続きは before に最後の投稿の ID、新しい投稿は after に最初の投稿の ID")
	@ApiResponse(responseCode = "200", description = "新しい順に20件")
	@ApiResponse(responseCode = "400", description = "tab が all・following でない、before・after が数でない（VALIDATION_ERROR）")
	@GetMapping
	TimelineResponse timeline(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(defaultValue = "all") String tab,
			@RequestParam(required = false) @Nullable Long before,
			@RequestParam(required = false) @Nullable Long after) {
		return postService.timeline(loginUser.id(), TimelineTab.parse(tab), before, after);
	}

	@Operation(summary = "新しい投稿の数", description = "画面が60秒ごとに呼び、「新しい投稿 N件」を出す")
	@ApiResponse(responseCode = "200", description = "after より新しい投稿の数（自分の投稿は数えない）")
	@ApiResponse(responseCode = "400", description = "tab・after の誤り（VALIDATION_ERROR）")
	@GetMapping("/new-count")
	NewPostCountResponse newCount(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(defaultValue = "all") String tab,
			@RequestParam(required = false) @Nullable Long after) {
		return postService.countNewer(loginUser.id(), TimelineTab.parse(tab), after);
	}

	@Operation(summary = "投稿する", description = "本文は280文字まで。画像（4枚まで）・動画（1本）は、先に /api/media で送ったキーを media に並べる")
	@ApiResponse(responseCode = "201", description = "投稿した")
	@ApiResponse(responseCode = "400", description = "入力の誤り（VALIDATION_ERROR。errors[].field は content・media）")
	@PostMapping
	ResponseEntity<PostResponse> create(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody PostRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(postService.create(loginUser.id(), request.content(), request.media()));
	}

	// {id:\d+}：数字だけ。/new-count などの URL と取り違えないように
	@Operation(summary = "投稿の詳細")
	@ApiResponse(responseCode = "200", description = "投稿")
	@ApiResponse(responseCode = "404", description = "投稿がない（POST_NOT_FOUND）")
	@GetMapping("/{id:\\d+}")
	PostResponse get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		return postService.get(loginUser.id(), id);
	}

	@Operation(summary = "投稿を編集する", description = "本人だけ。media は残すものは今のキー、足すものは一時置き場のキー。送らなければ今のまま")
	@ApiResponse(responseCode = "200", description = "編集した（editedAt が入る）")
	@ApiResponse(responseCode = "400", description = "入力の誤り（VALIDATION_ERROR。errors[].field は content・media）")
	@ApiResponse(responseCode = "404", description = "投稿がない・他人の投稿（POST_NOT_FOUND）")
	@PutMapping("/{id:\\d+}")
	PostResponse update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id,
			@Valid @RequestBody PostRequest request) {
		return postService.update(loginUser.id(), id, request.content(), request.media());
	}

	@Operation(summary = "投稿を削除する", description = "本人だけ")
	@ApiResponse(responseCode = "204", description = "削除した（画像・動画も S3 から消す）")
	@ApiResponse(responseCode = "404", description = "投稿がない・他人の投稿（POST_NOT_FOUND）")
	@DeleteMapping("/{id:\\d+}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		postService.delete(loginUser.id(), id);
		return ResponseEntity.noContent().build();
	}

}
