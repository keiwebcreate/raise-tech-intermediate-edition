package io.github.keiwebcreate.raisetechtimeline.user;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.keiwebcreate.raisetechtimeline.auth.LoginUser;
import io.github.keiwebcreate.raisetechtimeline.post.dto.TimelineResponse;
import io.github.keiwebcreate.raisetechtimeline.user.dto.ProfileResponse;
import io.github.keiwebcreate.raisetechtimeline.user.dto.ProfileUpdateRequest;
import io.github.keiwebcreate.raisetechtimeline.user.dto.UserSearchResponse;
import jakarta.validation.Valid;

/**
 * プロフィールの API（docs/features/profile.md 8章）。すべてログインが必要。
 *
 * | メソッド・URL                             | 中身 |
 * |---|---|
 * | GET /api/users?q=…&offset=                 | ユーザー検索（docs/features/user-search.md 8章。20件ずつ） |
 * | GET /api/users/{ユーザー名}                | プロフィール |
 * | GET /api/users/{ユーザー名}/posts?before=  | その人の投稿（新しい順に20件） |
 * | PUT /api/users/me                          | 自分のプロフィールを変える（表示名・自己紹介・アイコン） |
 *
 * ユーザー名は3文字以上なので、「me」という名前の人はいない（取り違えない）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	// 検索は /api/users/search にしない。「search」という名前のユーザーのプロフィール（/api/users/{ユーザー名}）とぶつかるため
	@GetMapping
	UserSearchResponse search(@AuthenticationPrincipal LoginUser loginUser, @RequestParam String q,
			@RequestParam(defaultValue = "0") int offset) {
		return userService.search(loginUser.id(), q, offset);
	}

	@GetMapping("/{username}")
	ProfileResponse profile(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username) {
		return userService.profile(loginUser.id(), username);
	}

	@GetMapping("/{username}/posts")
	TimelineResponse posts(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username,
			@RequestParam(required = false) @Nullable Long before) {
		return userService.posts(loginUser.id(), username, before);
	}

	@PutMapping("/me")
	ProfileResponse update(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return userService.update(loginUser.id(), request);
	}

}
