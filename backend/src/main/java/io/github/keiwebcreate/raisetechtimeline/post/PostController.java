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
@RestController
@RequestMapping("/api/posts")
public class PostController {

	private final PostService postService;

	public PostController(PostService postService) {
		this.postService = postService;
	}

	@GetMapping
	TimelineResponse timeline(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(defaultValue = "all") String tab,
			@RequestParam(required = false) @Nullable Long before,
			@RequestParam(required = false) @Nullable Long after) {
		return postService.timeline(loginUser.id(), TimelineTab.parse(tab), before, after);
	}

	@GetMapping("/new-count")
	NewPostCountResponse newCount(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(defaultValue = "all") String tab,
			@RequestParam(required = false) @Nullable Long after) {
		return postService.countNewer(loginUser.id(), TimelineTab.parse(tab), after);
	}

	@PostMapping
	ResponseEntity<PostResponse> create(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody PostRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(postService.create(loginUser.id(), request.content(), request.media()));
	}

	// {id:\d+}：数字だけ。/new-count などの URL と取り違えないように
	@GetMapping("/{id:\\d+}")
	PostResponse get(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		return postService.get(loginUser.id(), id);
	}

	@PutMapping("/{id:\\d+}")
	PostResponse update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id,
			@Valid @RequestBody PostRequest request) {
		return postService.update(loginUser.id(), id, request.content(), request.media());
	}

	@DeleteMapping("/{id:\\d+}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		postService.delete(loginUser.id(), id);
		return ResponseEntity.noContent().build();
	}

}
