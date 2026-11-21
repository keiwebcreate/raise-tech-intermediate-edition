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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "プロフィール・検索", description = "プロフィールとユーザー検索（docs/features/profile.md・user-search.md 8章）")
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	// 検索は /api/users/search にしない。「search」という名前のユーザーのプロフィール（/api/users/{ユーザー名}）とぶつかるため
	@Operation(summary = "ユーザー検索", description = "ユーザー名・表示名の部分一致。大文字・小文字、全角・半角は区別しない。先頭の @ は無視する")
	@ApiResponse(responseCode = "200", description = "一致の順位・フォロワーの多い順に20件。続きは offset に nextOffset")
	@ApiResponse(responseCode = "400",
			description = "q が空・50文字を超えた、offset が負や数でない（VALIDATION_ERROR。errors[].field は q・offset）")
	@GetMapping
	UserSearchResponse search(@AuthenticationPrincipal LoginUser loginUser, @RequestParam String q,
			@RequestParam(defaultValue = "0") int offset) {
		return userService.search(loginUser.id(), q, offset);
	}

	@Operation(summary = "プロフィール", description = "ユーザー名は大文字・小文字を区別しない")
	@ApiResponse(responseCode = "200", description = "プロフィール（フォロー数・フォロワー数・フォローの状態つき）")
	@ApiResponse(responseCode = "404", description = "ユーザーがいない（USER_NOT_FOUND）")
	@GetMapping("/{username}")
	ProfileResponse profile(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username) {
		return userService.profile(loginUser.id(), username);
	}

	@Operation(summary = "その人の投稿")
	@ApiResponse(responseCode = "200", description = "新しい順に20件")
	@ApiResponse(responseCode = "400", description = "続きの目印（before・after）が数でない（VALIDATION_ERROR）")
	@ApiResponse(responseCode = "404", description = "ユーザーがいない（USER_NOT_FOUND）")
	@GetMapping("/{username}/posts")
	TimelineResponse posts(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String username,
			@RequestParam(required = false) @Nullable Long before) {
		return userService.posts(loginUser.id(), username, before);
	}

	@Operation(summary = "自分のプロフィールを変える", description = "iconKey：今のキーなら残す、/api/media/icons のキーなら差し替える、null なら外す")
	@ApiResponse(responseCode = "200", description = "変えた")
	@ApiResponse(responseCode = "400", description = "入力の誤り（VALIDATION_ERROR。errors[].field は displayName・bio・iconKey）")
	@PutMapping("/me")
	ProfileResponse update(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return userService.update(loginUser.id(), request);
	}

}
